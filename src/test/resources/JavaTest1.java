package ru.ifmo.java.torrent.client;

import org.apache.commons.math3.random.RandomDataGenerator;
import ru.ifmo.java.torrent.files.FileInfo;
import ru.ifmo.java.torrent.protocol.Torrent;
import ru.ifmo.java.torrent.requests.*;
import ru.ifmo.java.torrent.utils.ConsolePrinter;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class Leecher implements Runnable, ServerRequester, ConsolePrinter {
    private final Lock lock = new ReentrantLock();
    private final Condition loadReady = lock.newCondition();

    private final File loadFolder;
    private final ServerBroker broker;
    private final ExecutorService pool;
    private final ConcurrentHashMap<Integer, FileInfo> files;

    private final ConcurrentLinkedQueue<Integer> filesToLoad = new ConcurrentLinkedQueue<>();
    private final LinkedHashMap<Integer, Torrent.ListResponse.File> localList = new LinkedHashMap<>();
    private final List<LeechWorker> workers = new LinkedList<>();
    private final Queue<String> log;

    private boolean isInterrupted = false;

    public Leecher(
            String loadFolderPath,
            ServerBroker broker,
            ExecutorService pool,
            ConcurrentHashMap<Integer, FileInfo> files,
            Queue<String> log
    ) {
        this.broker = broker;
        this.pool = pool;
        this.loadFolder = new File(loadFolderPath);
        this.loadFolder.mkdirs();
        this.files = files;
        this.log = log;
    }

    public void run() {
        try {
            lock.lockInterruptibly();
            while (!isInterrupted) {
                loadReady.await();
                loadFiles();
            }
            workers.forEach(LeechWorker::interrupt);
        } catch (InterruptedException e) {
            log.add("LEECHER THREAD INTERRUPTED");
        } catch (IOException e) {
            log.add("LEECHER EXCEPTION");
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    public void interrupt() {
        isInterrupted = true;
        readySignal();
    }

    public void readySignal() {
        try {
            lock.lockInterruptibly();
            loadReady.signal();
        } catch (InterruptedException e) {
            //ignore
        } finally {
            lock.unlock();
        }
    }

    public void loadFile(Integer id) {
        filesToLoad.add(id);
        readySignal();
    }

    private void loadFiles() throws IOException {
        while (!filesToLoad.isEmpty()) {
            Integer id = filesToLoad.poll();
            Torrent.ListResponse.File file = localList.get(id);
            if (file == null) {
                updateLocalList();
                file = localList.get(id);
                if (file == null) {
                    log.add("> file with id: " + id + " impossible to load from tracker (check `list` or try more)");
                    continue;
                }
            }
            ServerRequest serverRequest = new ServerRequest(buildRequest(getSourcesRequest(id)));
            broker.addRequest(serverRequest);
            Torrent.TrackerResponse trackerResponse = serverRequest.getResponse();
            Torrent.SourcesResponse response = trackerResponse.getSource();
            FileInfo fileInfo = null;
            if (files.containsKey(id)) {
                fileInfo = files.get(id);
            } else {
                fileInfo = createFileInfo(file);
            }
            for (Torrent.SourcesResponse.Client client : response.getClientsList()) {
                fileInfo.getSources().add(new Source(client.getIp().toByteArray(), client.getPort()));
            }
            createWorkers(fileInfo);
        }
    }

    private void createWorkers(FileInfo fileInfo) {
        Map<Source, List<Integer>> independentBlocks = getBlocksBySources(fileInfo.getId(),
                new ArrayList<>(fileInfo.getSources()));
        independentBlocks = getIndependentBlocksBySources(independentBlocks);

        for (Map.Entry<Source, List<Integer>> entry : independentBlocks.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            fileInfo.registerLeechWorker();
            LeechWorker worker = new LeechWorker(entry.getKey(), entry.getValue(), fileInfo, log);
            pool.submit(worker);
            workers.add(worker);
        }
        files.putIfAbsent(fileInfo.getId(), fileInfo);
    }

    private Map<Source, List<Integer>> getBlocksBySources(Integer fileId, List<Source> sources) {
        List<StatRequester> requesters = new LinkedList<>();
        for (Source source : sources) {
            StatRequester requester = new StatRequester(source, fileId);
            pool.submit(requester);
            requesters.add(requester);
        }
        Map<Source, List<Integer>> blocksBySources = new HashMap<>();
        for (StatRequester requester : requesters) {
            Torrent.ClientResponse response = requester.getResponse();
            if (response == null) {
                cout("> leecher sources response is null");
                continue;
            }
            blocksBySources.put(requester.getSource(), response.getStat().getPartsList());
        }
        return blocksBySources;
    }

    private Map<Source, List<Integer>> getIndependentBlocksBySources(Map<Source, List<Integer>> blocksBySources) {
        if (blocksBySources.size() <= 1) {
            return blocksBySources;
        }
        RandomDataGenerator generator = new RandomDataGenerator();
        Map<Integer, List<Source>> sourcesByBlocks = new HashMap<>();
        Map<Source, List<Integer>> independentBlocks = new HashMap<>();

        blocksBySources.keySet().forEach(source -> independentBlocks.put(source, new LinkedList<>()));

        blocksBySources.values().parallelStream()
                .flatMap(Collection::stream)
                .collect(Collectors.toSet())
                .forEach(blockId -> sourcesByBlocks.put(blockId, new LinkedList<>()));

        for (Map.Entry<Source, List<Integer>> entry : blocksBySources.entrySet()) {
            for (Integer blockId : entry.getValue()) {
                sourcesByBlocks.get(blockId).add(entry.getKey());
            }
        }
        for (Map.Entry<Integer, List<Source>> entry : sourcesByBlocks.entrySet()) {
            Integer blockId = entry.getKey();
            Source source = null;
            if (entry.getValue().size() == 1) {
                source = entry.getValue().get(0);
            } else {
                int sourceId = generator.nextInt(0, entry.getValue().size() - 1);
                source = entry.getValue().get(sourceId);
            }
            independentBlocks.get(source).add(blockId);
        }
        return independentBlocks;
    }

    private FileInfo createFileInfo(Torrent.ListResponse.File file) throws IOException {
        String path = loadFolder + File.separator + file.getName();
        return new FileInfo(file.getId(), path, file.getName(), file.getSize(), true);
    }

    private void updateLocalList() {
        localList.clear();
        ServerRequest serverRequest = new ServerRequest(buildRequest(getListRequest()));
        broker.addRequest(serverRequest);
        Torrent.TrackerResponse trackerResponse = serverRequest.getResponse();
        Torrent.ListResponse response = trackerResponse.getList();
        for (Torrent.ListResponse.File file : response.getFilesList()) {
            localList.put(file.getId(), file);
        }
    }

    public void loadStatus() {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append(String.format("Current active files %d", files.size()));
        for (Map.Entry<Integer, FileInfo> file : files.entrySet()) {
            logMessage.append("\n\t").append(file.toString());
        }
        log.add(logMessage.toString());
    }
}
