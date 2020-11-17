# Code summarization dataset

Tool for mining data from GitHub for code summarization tasks.

## Installation and launch

Follow these steps to run the tool:
- Clone repo from Github
```
git clone https://github.com/JetBrains-Research/code-summarization-dataset.git
```
- Set up config

#### In **main.kt**
- provide patches to:

    - text file with your GitHub token:
    
        ```val TOKEN_PATH = "repos/token.txt"```

    - .json config file:
    
        ```val CONFIG_PATH = "repos/config.json"```

    - .json file with list of repositories URLs in format ```[...]/{OWNER}/{NAME}``` (exactly 2 slashes ```[...]/JetBrains/Kotlin```)  
    
        ```val URLS_PATH = "repos/urls.json"```
  
    - provide path to results dump directory:
    
      ```val DUMP_DIR = "repos/results"```

#### Config 

Config is .json file:
```
{
    "languages": ["Java", "Kotlin"],    // list of languages
    "stars_count": [">=", 10],          // relations with integers
    "commits_count": [0, 100000],       // integer ranges
    "contributors_count": [">=", 10],
    "anon_contributors": [true],        // flag
    "watchers_count": [],               // empty list == no filter
    "forks_count": [10, 100000],
    "open_issues_count": [0, 100000],
    "subscribers_count": [],
    "size_KB": ["<=", 10000000],
    "created_at": [">=","2010-01-01"],  // relations with dates
    "updated_at": ["2010-01-01", "2015-01-01"], // dates ranges
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

#### Run and results
```
./gradlew run
```

In ```DUMP_DIR``` appear 4 files and 2 folders:

  - folders ```good(bad)``` each with inner folder ```explain```  
  - files ```good(bad)_input_urls.jsonl``` -- good (bad) input urls
  - files ```good(bad)_repos.jsonl``` -- all good (bad) traversed repos
  
```good(bad)``` folders contain repositories summary for each repository

```good(bad)/explain``` contain explanation about results of applied filters (from ```config``` file) to each repository

### Data sources

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
