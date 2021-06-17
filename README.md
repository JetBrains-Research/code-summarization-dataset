# Code summarization dataset

Tool for mining data from GitHub for code summarization tasks.

## Installation

Follow these steps to run the tool:
- Clone repo from Github
```commandline
git clone https://github.com/JetBrains-Research/code-summarization-dataset.git
```

# Tool modules

**I. Filtration** - filtering input GitHub repositories urls with specified filters (config - `config/filter_config.json`)

**II. Analysis** - methods summary extraction from repository or local directory (config - `config/analysis_config.json`)

**III. Provider** - filtration + analysis = continuous filtration end analysis of repositories

## I. Repositories filtering (filtration module)

**Input:** list of urls to existing GitHub repositories in format `.../REPOOWNER/REPONAME` (exactly 2 slashes) and filtration config

**Output:** lists of 'good' and 'bad' repositories after filters applying with explanation about filters results

### 1. Filtration config

filtration config is .json file with all repo filters and run parameters:
```
{
    "token_path" : "config/token.txt",           // path to GitHub token
    "dump_dir_path" : "config/filter_results",   // dump directory path
    "repos_urls_path": "config/urls.json",       // path to repos URLs
    "languages": ["Java", "Python"],             // list of languages
    "stars_count": [">=", 10],                   // relations with integers
    "is_fork": [false],                          // boolean flag
    "is_license": [true],
    "licenses": ["gpl-3.0", "apache-2.0"],       // list of licenses
    "commits_count": [0, 100000],                // integer ranges
    "contributors_count": [">=", 10],
    "anon_contributors": [true],                 
    "watchers_count": [],                        // empty list == no filter
    "forks_count": [10, 100000],
    "open_issues_count": [0, 100000],
    "subscribers_count": [],
    "size_KB": ["<=", 10000000],
    "created_at": [">=","2010-01-01"],           // relations with dates
    "updated_at": ["2010-01-01", "2015-01-01"],  // dates ranges
    "pushed_at": []
}
```

**GitHub token**:
- filtration requires a [GitHub API personal access token](https://docs.github.com/en/free-pro-team@latest/github/authenticating-to-github/creating-a-personal-access-token) without any special permissions
- token is 40 symbols code that must be located in a separate file on the first line without additional data
- file with path `token_path` must contain this token
  ```json
  file: filter_config.json
  
  ...
  "token_path": token.txt
  ...
  ```
  ```json
  file: token.txt
  
  496**********************************4cf
  ```

**Rules**:
- each parameter must be specified in brackets **[params, ...]**
- dates in ```"YYYY-MM-DD"``` format with quotes
- all integer filters support relation (>, <, <=, >=, =) in quotes **["<", N]**
- all integer filters support ranges in brackets **[min incl., max incl.]**
- all date filters support relation (>, <, <=, >=, =) in quotes **[">=", "YYYY-MM-DD"]**
- all date filters support ranges in brackets **[min incl., max incl.]**
- all date and integer filters support implicit EQ (=) relation **[N]** == **["=", N]**
- licenses:
    -  `"is_license": []` - repository hasn't or has any license, values in `"licenses": [...]` field are ignored
    -  `"is_license": [false]` - repository hasn't license, values in `"licenses": [...]` field are ignored
    -  `"is_license": [true]` - repository has license
        - `"licenses": []` - repository has any license
        - `"licenses": [...]` - repository has license from the list
- licenses not bound in the code
- licenses filter running by `keyword` from [GitHub licenses list](https://docs.github.com/en/free-pro-team@latest/github/creating-cloning-and-archiving-repositories/licensing-a-repository#searching-github-by-license-type) provided by `license/key` path in GitHub API v3 summary `https://api.github.com/repos/REPOOWNER/REPONAME` for each repository        

**Examples**:
- ```"languages": ["Kotlin", "C++", "Haskell"] --> repository main language is Kotlin OR C++ OR Haskell```
- ```"[param]_count": [42] --> repository [param]_count == 42 ```
- ```"[param]_count": ["<=", 42] --> repository [param]_count <= 42 ```
- ```"[param]_count": [42, 128] --> 42 <= repository [param]_count <= 128 ```
- ```"pushed_at": ["2010-01-01"] --> repository push date = 2010.01.01```
- ```"created_at": [">=","2010-01-01"] --> repository creation date >= 2010.01.01```
- ```"updated_at": ["2010-01-01", "2015-01-01"] --> 2010.01.01 <= repository update date <= 2015.01.01```

### 2. Run


- in .json file `repos_urls_path` add any GitHub repositories in format `.../REPOOWNER/REPONAME` (exactly 2 slashes)
  ```json
  file: filter_config.json
  
  ...
  "repos_urls_path": urls.json
  ...
  ```
  ```json
  file: urls.json
  
  [ "/JetBrains/Kotlin", "/JetBrains/intellij-community"] 
  or 
  ["https://github.com/JetBrains/Kotlin", "https://github.com/JetBrains/intellij-community"] 
  ```  

#### 2.1 as code in project
- import `FilterConfig` and `ReposFilter` classes
- provide a path to `filter_config.json`, initialize config and finder
  ```kotlin
  val filterConfig = FilterConfig(configPath = filterConfigPath, isDebug = isDebug)
  val reposFinder = ReposFilter(config = filterConfig)
  reposFinder.run()
  ```

#### 2.2 as separate module 
- write own entry point
  ```kotlin
  import filtration.utils.FinderParser

  fun main(args: Array<String>) = FilterParser().main(args)
  ```

- run with script and command line arguments
  ```shell
  #!/bin/bash
  ./gradlew :run --args="--mode=filter --fd --fc config/filter_config.json"
  ```

- arguments
  
      -m, --mode                         - work mode: filter, analysis, filter-analysis
      --fc, --filter-config              - path to filtration config .json file
      --fd, --filter-debug, -d --debug   - flag, print all log messages to the console

### 3. Results

In `dump_dir_path` appear 4 files and 2 folders:

  - folders ```good(bad)``` each with inner folder ```explain```  
  - files ```good(bad)_input_urls.jsonl``` -- good (bad) input urls
  - files ```good(bad)_repos.jsonl``` -- all good (bad) traversed repos

```good(bad)``` folders contain repositories summary for each repository

```good(bad)/explain``` contain explanation about results of applied filters (from ```config``` file) to each repository

### 4. Search data sources

**commits_count** - 1 [GraphQL](https://developer.github.com/v4/explorer/) query:
  ```gql
  query {
    repository(owner: "JetBrains-Research", name: "code-summarization-dataset") {
      defaultBranchRef {
        target {
          ... on Commit {
            history (first: 1) {
              totalCount
              pageInfo
              { endCursor }
            }
          }
        }
      }
    }
  }
  ```

**contributors_count** - 1 [API v3](https://docs.github.com/en/free-pro-team@latest/rest) query with pagination hack (1 contributor per_page + number of pages):
  ```
  https://api.github.com/repos/jetbrains/kotlin/contributors?per_page=1&anon=false
  ```
**others** from repository summary **api/repos/owner/name** - 1 [API v3](https://docs.github.com/en/free-pro-team@latest/rest) query:
  ```
  https://api.github.com/repos/jetbrains/kotlin
  ```

## II. Repositories analysis (analysis module)

**Currently supported languages: Java, Python**

- for Python data retrieving you need [pythonparser](https://github.com/JetBrains-Research/pythonparser) in your system PATH


**Input:** repository or directory and analysis config

**Output:** summary about all functions from repository or directory for supported languages:

- function name and fullname
- function documentation or multiline comment
- function body
- function AST
- function AST paths in code2seq format
- metadata (file path, commit info, extraction statistics)

### 1. Analysis config

analysis config is .json file with run parameters:

```
{
  "HISTORY_MODE": true,                               // main feature of tool (see below) 

  "repos_urls_path": "repos/repos.json",              // path to .json list with urls (in format /{OWNER}/{NAME}) to GitHub repos 
  "dirs_list_path": "repos/dirs.json",                // path to .json list with paths to local directories
  "dump_dir_path" : "repos/analysis_results",         // path to tool dump directory

  "workers_count": 3,                                 // how many workers in parallel (thread pool size)  
  "log_dump_threshold": 200,                          // log messages dump to file threshold
  "summary_dump_threshold": 200,                      // methods summary dump threshold

  "gzip_files": true,                                 // whether the extracted data should be gziped
  "remove_after_gzip": false                          // whether the all not gziped extracted data should be deleted
  "remove_repo_after_analysis": false,                // whether the repository should be deleted after analysis
  
  "commits_type": "merges",                           // commits type (merges or first_parents, see explanation below)
  "min_commits_number": 0,                            // minimum number of commits of selected type for analysis start
  "merges_part_in_history": 0.005,                    // part of merge commits in first_parents history (see below)
  
  "task": "name",                                     // current supported task: name extraction
  "parser": "gumtree"                                 // current supported parser: gumtree
  "granularity": "method",                            // current supported granularity: method
  "languages": ["Java", "Python"],                    // current supported languages: Java, Python
  "method_uniqueness": ["full_name", "name", "file", "return_type", "args_types"] // how to check method uniqueness 
  
  "hide_methods_names": true,                         // hides methods names in methods bodies and AST's
  "exclude_constructors": true,                       // exclude constructors from summary

  "min_body_lines_length": 0,                         // minimum lines length of method body 
  "exclude_with_exact_name": ["name1", "name2"],      // methods with these names will not be collected
  "exclude_with_name_prefix": ["test"],               // methods with these prefixes of methods name will not be collected

  "JAVA_exclude_with_annotations": ["@Override"],     // Java methods with these annotations will not be collected

  "max_paths": 1000,                                  // upper bound for number of retrived paths (code2seq) 
  "max_path_width": 2,                                // path max width
  "max_path_length": 9,                               // path max length (number of tokens) 
  "exclude_nodes": [],                                // exclude nodes from AST and code2sec paths
  "exclude_doc_node": true,                           // exclude documentation node from AST adn code2sec paths 
  "ast_dot_format": false,                            // AST dump format: dot or our version (dot with identifiers in nodes)
  "code2sec_format_dump": true                        // dump AST in code2sec format 'method|name node,PATH,node'
}
```

### History processing

If `"HISTORY_MODE": false`:
- data from repositories is extracted without git-history
- data from directories is always extracted without git-history 

If `"HISTORY_MODE": true` - data extraction based on git-history, analyzer:
- loads commit history from default branch of repository
- moves from the oldest (first) commit to the newest (last)
- for every consecutive pair of commits gets the diff list of files `git diff --name-only SHA1 SHA2`
- filters supported languages files from diff list
- if list of files for supported languages isn't empty - makes checkout to current commit `git checkout SHA`
- extracts new methods summary from files if tuple `"method_uniqueness": [...]` wasn't added before, parameters of tuple:
  - `"name"` - method name, e.g. `foo`, `bar`
  - `"full_name"` - method fullname (nesting hierarchy: all parents classes and functions), e.g. `MyClass.foo`, `foo.bar`
  - `"return_type"` - method return type
  - `"args_types"` - types of methods arguments (if possible to extract)
  - `"file"` - path to file with method
  - if `"method_uniqueness": []` - tool extracts all methods without uniqueness check

Two types of history processing depending on the type of commit:
- `"commits_type": "merges"` - history includes merge commits `git log --first-parent --merges DEFAULT_BRANCH`
- `"commits_type": "first_parents"` - history includes first-parents commits `git log --first-parent DEFAULT_BRANCH`
- both history types include oldest and youngest commits (merge or not merge)
- `"merges_part_in_history": 0.005` - this is an attempt to distinguish repositories using rebase-based history from merge-based history,
  e.g. for [Kotlin repository](https://github.com/JetBrains/Kotlin):

  `git log --first-parent --pretty=oneline | wc -l` is **66016** first parents commits

  `git log --first-parent --merges --pretty=oneline | wc -l` is **512** merge commits

  `merges_part_in_history = 512 / 66016 = 0.00776`

  => if we set `merges_part_in_history = 0.01`, then the repository will not be analyzed because the repository value is below the value we set (`0.00776 [real value] < 0.01 [value in config]`)

  *set `merges_part_in_history = 0.0`  if you do not have enough statistics


### 2. Run

#### 2.1 as code in project
- import `AnalysisConfig`, `Analyzer` and `AnalysisRepository` classes
- provide a path to `analysis_config.json`, initialize config and analyser
- `submitRepo` (or `submitAllRepos`) any number of repositories for analysis
  
  ```kotlin  
  // config for analysis
  val analysisConfig = AnalysisConfig(
      configPath = analysisConfigPath,
      isDebugAnalyzer = isAnalyserDebug, // log in console only submission and done workers statuses 
      isDebugWorkers = isWorkersDebug    // log in console all workers messages
  )
  val reposUrls = loadJSONList(analysisConfig.reposUrlsPath).parseRepoUrls()
  val dirsPaths = loadPaths(analysisConfig.dirsListPath)

  val reposAnalyzer = Analyzer(config = analysisConfig)

  // submit not loaded repo
  reposAnalyzer.submitRepo(AnalysisRepository(owner = "owner", name = "name"))
  // submit already loaded repo  
  reposAnalyzer.submitRepo(AnalysisRepository(path = "repo/path", owner = "owner", name = "name"))
  // submit repos from list
  reposAnalyzer.submitRepos(reposUrls.map { AnalysisRepository(owner = it.first, name = it.second) })
  // submit file
  reposAnalyzer.submitFile("my/file.java")
  // submit directory
  reposAnalyzer.submitFile("my/python/files")
  // submit any files or directories  
  reposAnalyzer.submitFiles(dirsPaths)

  // blocking waiting until any worker running  
  reposAnalyzer.waitUnitAnyRunning()
  ```

#### 2.2 as separate module
- provide config paths
  ```json
  file: analysis_config.json

  ...
  "repos_urls_path": urls.json
  "dirs_list_path": dirs.json
  ...
  ```
  - `repos_urls_path` path to .json GitHub repositories list in format `.../REPOOWNER/REPONAME` (**exactly 2 slashes**)
    ```json
    file: urls.json
    
    [ "/JetBrains/Kotlin", "/JetBrains/intellij-community"] 
    or
    ["https://github.com/JetBrains/Kotlin", "https://github.com/JetBrains/intellij-community"] 
    ```
  - `dirs_list_path` - path to .json list with local directories paths

    ```json
    file: dirs.json
    
    [ "local/directory", "local/SomeClass.java" ]
    ```

- write own entry point
  ```kotlin
  import analysis.utils.AnalyzerParser
  
  fun main(args: Array<String>) = AnalyzerParser().main(args)
  ```

- run with script and command line arguments
  ```shell
  #!/bin/bash
  ./gradlew :run --args="--mode=analysis --ad --ac config/analysis_config.json"
  ```

- arguments
  ```
  -m, --mode               - work mode: filter, analysis, filter-analysis
  --ac, --analysis-config  - path to analysis config .json file
  --ad, --analysis-debug   - flag, print all log messages to the console
  --wd, --workers-debug    - flag, print all workers messages to the console
  -d, --debug              - flag, print all log messages to the console
  ```


### 3. Results

In `dump_dir_path` appear 4 files:
  - `info.json`         -- repository / file / dir analysis summary 
  - `methods.jsonl`     -- all methods summary (one method per line)
  - `paths.jsonl`       -- all retrieved paths for every method (one method per line)
  - `paths.c2s`         -- all retrieved paths for every method in code2seq file format
  - `commits_log.jsonl` -- all consecutive traversed commits pairs (one pair per line)
  - `work_log.txt`      -- log file


## III. Repositories filtering and analysis (module provider)

Filtration + analysis modules

**Input:** list of urls to existing GitHub repositories, filtration and analysis configs

**Output:**
1. lists of 'good' and 'bad' repositories after filters applying with explanation about filters results
2. for each 'good' repository all summary information extracted with analysis module

### 1. Run

- prepare two .json configs `filter_config.json` and `analysis_config.json`
- prepare list of GitHub repositories `repos_urls_path` in `filter_config.json` **(see I, 2)**
- files with repositories and directories paths lists from `analysis_config.json` 
  in `repos_urls_path` and `dirs_list_path` will be ignored
  
##### Workflow
- tool takes repository from file provided in `repos_urls_path` from `filter_config.json`
- tool applies filters to the repository from `filter_config.json`
- if all filters are successful
  - repository url is sent to analysis module 
  - analysis module downloads repository and retrieves all necessary data with parameters from `analysis_config.json`
  - for each repository tool stores all data in repository folder in `dump_dir_path` from `analysis_config.json`  

#### 1.1 as code in project

  - import `FilterConfig`, `AnalysisConfig` and `FilterAnalyserProvider` classes 
  - provide paths to `filter_config.json` and `analysis_config.json`, initialize configs and provider

    ```kotlin
    val filterConfig = FilterConfig(configPath = filtrationConfigPath, isDebug = isSearchDebug)
    val analysisConfig = AnalysisConfig(
        configPath = analysisConfigPath,
        isDebugAnalyzer = isAnalyserDebug,
        isDebugWorkers = isWorkersDebug
    )
    val provider = FilterAnalyserProvider(filterConfig = filterConfig, analysisConfig = analysisConfig)
    provider.run()
    ```

#### 1.2 as separate module
- write own entry point
  ```kotlin
  import provider.utils.ProviderParser
  
  fun main(args: Array<String>) = ProviderParser().main(args)
  ```

- run with script and command line arguments:

  ```shell
  #!/bin/bash
  ./gradlew :run --args="--mode=filter-analysis --fd --ad --fc config/filter_config.json --ac config/analysis_config.json"
  ```

- arguments:
  ```
  -m, --mode               - work mode: filter, analysis, filter-analysis
  --fc, --filter-config    - path to filtration config .json file
  --ac, --analysis-config  - path to analysis config .json file
  --fd, --filter-debug     - flag, print all filtration module log messages to the console
  --ad, --analysis-debug   - flag, print all analysis module log messages to the console
  --wd, --workers-debug    - flag, print all workers messages to the console
  -d, --debug              - flag, print all log messages to the console
  ```
### 2. Results

- output from filtration module
- for each repository output from analysis module to folder `dump_folder/data/REPOOWNER__REPONAME`

