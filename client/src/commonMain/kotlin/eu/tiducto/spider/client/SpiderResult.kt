package eu.tiducto.spider.client

sealed class SpiderResult<out R> {
    data class Success<out T>(val data: T) : SpiderResult<T>()
    data class Error(val error: SpiderError) : SpiderResult<Nothing>()
}
