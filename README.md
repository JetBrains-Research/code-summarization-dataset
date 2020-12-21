# Code summarization dataset

Tool for mining data from GitHub for code summarization tasks.

## Installation and launch

Follow these steps to run the tool:
- Clone repo from Github
```
git clone https://github.com/JetBrains-Research/code-summarization-dataset.git
```
## Repositories analysis (reposanalyzer module)

**Input:** repository and analysis config

**Output:** summary about all functions from repository for supported languages (Java):

- function name and fullname
- function documentation or multiline comment
- function body
- function AST
- metadata (file path, commit info)


### 1. Analysis config

analysis config is .json file with run parameters:

```
{
  "repos_dirs_list_path": "repos/repos.json",         // path to .json list with paths to local repositories
  "dump_dir_path" : "repos/analysis_results",         // path to dump directory
  "languages": ["Java"],                              // interesting languages
  "commits_type": "merges",                           // commits type (merges or first_parents, see explanation below)
  "task": "name",                                     // current supported task - name extraction
  "granularity": "method",                            // current supported granularity - method
  "hide_methods_names": true,                         // hides methods names in methods bodies and AST's
  "exclude_constructors": true,                       // exclude constructors from summary
  "exclude_nodes": [],                                // unsupported
  "threads_count": 3,                                 // how many repositories are analyzed in parallel (thread pool size)  
  "log_dump_threshold": 200,                          // log messages dump to file threshold
  "summary_dump_threshold": 200,                      // methods summary dump threshold
  "remove_repo_after_analysis": false,                // whether the repository should be deleted after analysis
  "gzip_files": true,                                 // whether the repository should be gziped
  "remove_after_gzip": false,                         // whether the all not gziped dump data should be deleted
  "copy_detection": false                             // EXPERIMENTAL - not tested, maybe slow and incorrect
}
```

#### History processing

Repositories analysis based on git-history, analyzer:
- loads commit history from default branch of repository
- moves from the oldest (first) commit to the newest (last)
- for every consecutive pair of commits gets the diff list of files `git diff --name-only SHA1 SHA2`
- filters supported languages (Java) files from diff list
- if list of files for supported languages isn't empty -- makes checkout to current commit `git checkout SHA`
- extracts new methods summary from files if their (methods) pair `<source file path, full method name (nesting hierarchy)>` wasn't added before

Two types of history processing depending on the type of commit:
- `"commits_type": "merges"` - history includes merge commits `git log --first-parent --merges DEFAULT_BRANCH`
- `"commits_type": "first_parents"` - history includes first-parents commits `git log --first-parent DEFAULT_BRANCH`
- both history types include oldest and youngest commits   

### 2. Run

run example with provided script `run_analyzer.sh`:

    #!/bin/bash
    ./gradlew :reposanalyzer:run --args="--debug -a ../repos/analysis_config.json"

arguments:

    -a, --analysis    - path to analysis config .json file
    --debug           - flag, print all log messages to the console


### 3. Results

In `dump_dir_path` appear 4 files:

  - `methods.jsonl`     -- all methods summary (one method per line)
  - `commits_log.jsonl` -- all consecutive traversed commits pairs (one pair per line)
  - 2 files with log
