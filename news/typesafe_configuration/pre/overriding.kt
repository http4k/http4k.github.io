package content.news.typesafe_configuration.pre

val name = System.getProperty("USERNAME") ?: System.getenv("USERNAME") ?: "DEFAULT_USER"
