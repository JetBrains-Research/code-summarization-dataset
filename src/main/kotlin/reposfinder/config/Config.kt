package reposfinder.config

/*
 *  More supported settings in ReposSearchSettings
 *  and https://docs.github.com/en/free-pro-team@latest/rest/reference/search#ranking-search-results
 */
object Config {
    val settings = ConfigSettings().setUp {
        // API v3
        stars(40..50)
        language("kotlin")
        created("2010-05-12", "2017-10-20")
        contributors(GT, 10)
        // GraphQL API
        firstCommit(GT, "2015-01-01")
        commits(GT, 10)
    }

    /*
     *   В API GitHub нет возможности получить общее число contributors репозитория.
     *   Можно получить лишь максимум 100 contributors на страницу по конкретному репозиторию
     *   и число страниц, но если их очень много, то это слишком много запросов к API.
     *
     *   Есть хак с pagination: спрашиваем всех contributors репозитория с выдачей 1 на страницу,
     *                          считаем количество страниц = число contributors.
     *   Проблема в том, что это не то количество, которое отображается на web-странице репозитория.
     *   Обычно результат через хак с pagination меньше количества на web-странице.
     *
     *   Помимо этого у репозитория есть анонимные contributors, если добавить к запросу флаг anon=true,
     *   то количество через хак с pagination возрастает.
     *
     *   Если по хаку взять среднее между anon=true и anon=false, то оно примерно равно числу в веб-версии.
     *
     *   Соответственно, если anonContributors = true, то все расчеты про число contributors будут с учетом
     *   анонимных, если false => фильтр только по числу открытых contributors.
     *
     */
    const val anonContributors = false
}
