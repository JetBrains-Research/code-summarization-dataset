# Code summarization dataset

Tool for mining data from GitHub for code summarization tasks.

## Installation and launch

Follow these steps to run the tool:
- Clone repo from Github
```
git clone https://github.com/JetBrains-Research/code-summarization-dataset.git
```

## Repositories filtering (reposfinder module)

**Input:** list of urls to existing GitHub repositories in format `.../REPOOWNER/REPONAME` (exactly 2 slashes) and search config

**Output:** lists of 'good' and 'bad' repositories after filters applying with explanation about filters results

### 1. Search config

search config is .json file with all search filters and run parameters:
```
{
    "token_path" : "repos/token.txt",            // path to GitHub token
    "dump_dir_path" : "repos/search_results",    // dump directory path
    "repos_urls_path": "repos/urls.json",        // path to repos URLs
    "languages": ["Java", "Kotlin"],             // list of languages
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

**Rules**:
- each parameter must be specified in brackets **[params, ...]**
- dates in ```"YYYY-MM-DD"``` format with quotes
- all integer filters support relation (>, <, <=, >=, =) in quotes **["<", N]**
- all integer filters support ranges in brackets **[min incl., max incl.]**
- all date filters support relation (>, <, <=, >=, =) in quotes **[">=", "YYYY-MM-DD"]**
- all date filters support ranges in brackets **[min incl., max incl.]**
- all date and integer filter support implicit EQ (=) relation **[N]** == **["=", N]**
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

run example with provided script `run_finder.sh`:

    #!/bin/bash
    ./gradlew :reposfinder:run --args="--debug -s ../repos/search_config.json"
    
arguments:

    -s, --search    - path to search config .json file
    --debug         - flag, print all log messages to the console
    
### 3. Results

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