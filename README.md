# Code summarization dataset

Tool for mining data from GitHub for code summarization tasks.

## Installation and launch

Follow these steps to run the tool:
- Clone repo from Github
```
git clone https://github.com/JetBrains-Research/code-summarization-dataset.git
```
# Tool modules

**I. reposfinder** - filtering input repositories urls with specified filters (config - `repos/search_config.json`)

**II. reposanalyzer** - methods summary extraction from loaded repository (config - `repos/analysis_config.json`)

**III. reposprovider** - reposfinder + reposanalyzer = continuous filter end analysis of repositories


## I. Repositories filtering (reposfinder module)

**Input:** list of urls to existing GitHub repositories and search config

**Output:** lists of 'good' and 'bad' repositories after filters applying with explanation about filters results

#### 1. Search config

search config is .json file with all search filters and run parameters:
```
{
    "token_path" : "repos/token.txt",            // path to GitHub token
    "dump_dir_path" : "repos/search_results",    // dump directory path
    "repos_urls_path": "repos/urls.json",        // path to repos URLs
    "languages": ["Java", "Kotlin"],             // list of languages
    "stars_count": [">=", 10],                   // relations with integers
    "is_fork": [false],                          // boolean flag
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

Rules:
- each parameter must be specified in brackets **[params, ...]**
- dates in ```"YYYY-MM-DD"``` format with quotes
- all integer filters support relation (>, <, <=, >=, =) in quotes **["<", N]**
- all integer filters support ranges in brackets **[min incl., max incl.]**
- all date filters support relation (>, <, <=, >=, =) in quotes **[">=", "YYYY-MM-DD"]**
- all date filters support ranges in brackets **[min incl., max incl.]**
- all date and integer filter support implicit EQ (=) relation **[N]** == **["=", N]**

Examples:
- ```"languages": ["Kotlin", "C++", "Haskell"] --> repository main language is Kotlin OR C++ OR Haskell```
- ```"[param]_count": [42] --> repository [param]_count == 42 ```
- ```"[param]_count": ["<=", 42] --> repository [param]_count <= 42 ```
- ```"[param]_count": [42, 128] --> 42 <= repository [param]_count <= 128 ```
- ```"pushed_at": ["2010-01-01"] --> repository push date = 2010.01.01```
- ```"created_at": [">=","2010-01-01"] --> repository creation date >= 2010.01.01```
- ```"updated_at": ["2010-01-01", "2015-01-01"] --> 2010.01.01 <= repository update date <= 2015.01.01```

#### 2. Run

run example with provided script `run_finder.sh`:

    #!/bin/bash
    ./gradlew :reposfinder:run --args="--debug -s ../repos/search_config.json"
    
arguments:

    -s, --search    - path to search config .json file
    --debug         - flag, print all log messages to the console
    
#### 3. Results

In `dump_dir_path` appear 4 files and 2 folders:

  - folders ```good(bad)``` each with inner folder ```explain```  
  - files ```good(bad)_input_urls.jsonl``` -- good (bad) input urls
  - files ```good(bad)_repos.jsonl``` -- all good (bad) traversed repos

```good(bad)``` folders contain repositories summary for each repository

```good(bad)/explain``` contain explanation about results of applied filters (from ```config``` file) to each repository

### 4. Search data sources

**commits_count** - 1 [GraphQL](https://developer.github.com/v4/explorer/) query:
```
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

## II. Repositories analysis (reposanalyzer module)

**Input:** repository and analysis config

**Output:** summary about all functions from repository for supported languages (Java): 
    
- function name and fullname
- function documentation or multiline comment
- function body
- function AST
- metadata (file path, commit info)


#### 1. Analysis config

analysis config is .json file with run parameters:

```
{
  "repos_dirs_list_path": "../repos/repos.json",      // path to .json list with paths to local repositories
  "dump_dir_path" : "../repos/analysis_results",      // path to dump directory
  "languages": ["Java"],                              // interesting languages 
  "commits_type": "merges",                           // commits type (merges or first_parents, see explanation below)
  "task": "name",                                     // current supported task - name extraction
  "granularity": "method",                            // current supported granularity - method
  "hide_methods_names": true,                         // hides methods names in methods bodies and AST's
  "exclude_constructors": true,                       // exclude constructors from summary
  "exclude_nodes": [],                                // unsupported
  "log_dump_threshold": 200,                          // log messages dump to file threshold 
  "summary_dump_threshold": 200,                      // methods summary dump threshold
  "remove_repo_after_analysis": false,                // whether the repository should be deleted after analysis
  "gzip_files": true,                                 // whether the repository should be gziped
  "remove_after_gzip": false,                         // whether the all not gziped dump data should be deleted
  "copy_detection": false                             // EXPERIMENTAL - not tested, maybe slow and incorrect
}
```

#### 2. Run

run example with provided script `run_analyzer.sh`:

    #!/bin/bash
    ./gradlew :reposanalyzer:run --args="--debug -a ../repos/analysis_config.json"
    
arguments:

    -a, --analysis    - path to analysis config .json file
    --debug           - flag, print all log messages to the console
    
    
#### 3. Results

In `dump_dir_path` appear 4 files:

  - `methods.jsonl`     -- all methods summary (one method per line)
  - `commits_log.jsonl` -- all consecutive traversed commits pairs (one pair per line)
  - 2 files with log


### III. Repositories filtering and analysis (module reposprovider)

reposfinder + reposanalyzer modules

**Input:** list of urls to existing GitHub repositories, search and analysis configs

**Output:** 
1. lists of 'good' and 'bad' repositories after filters applying with explanation about filters results
2. for each 'good' repository all summary information extracted with reposanalyzer module

#### 1. Run

run example with provided script `run_provider.sh`:

    #!/bin/bash
    ./gradlew :reposprovider:run --args="--debug -s ../repos/search_config.json -a ../repos/analysis_config.json"
    
arguments:

    -s, --search      - path to search config .json file
    -a, --analysis    - path to analysis config .json file
    --debug           - flag, print all log messages to the console

#### 2. Results

- output from reposfinder module
- for each repository output from reposanalyzer module to folder `dump_folder/REPOOWNER__REPONAME`


