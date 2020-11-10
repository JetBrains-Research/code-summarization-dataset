# Code summarization dataset

Tool for mining data from GitHub for code summarization tasks.

## Installation and launch

Follow these steps to run the tool:
- Clone repo from Github
```
git clone https://github.com/JetBrains-Research/code-summarization-dataset.git
```
- Set up config

TODO

In **main.kt**:
- provide path to .json file with list of patches to cloned git repositories

  ```val PATH_TO_ALL_REPOS_LIST = "repos/repos.json"```
  
- provide path to results dump folder:

  ```val DUMP_FOLDER = "repos/dumps"```


Run the tool
```
./gradlew run
```

Two files appear in the dump folder for each repository:

  - ```methods.jsonl``` -- line-delimited json methods summary
  - ```log.jsonl``` -- all traversed commits

