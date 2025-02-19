import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import java.rmi.server.ServerNotActiveException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private const val LATENCY = 700L
private const val RESPONSE_LENGTH = 2048

fun main() {
    // Функции можно вызывать отсюда для проверки
    //  для ДЗ лучше использовать blockingSubscribe вместо subscribe потому что subscribe подпишется на изменения,
    //  но изменения в большинстве случаев будут получены позже, чем выполнится функция main, поэтому в консоли ничего
    //  не будет выведено. blockingSubscribe работает синхронно, поэтому результат будет выведен в консоль
    //
    //  В реальных программах нужно использовать subscribe или передавать данные от источника к источнику для
    //  асинхронного выполнения кода.
    //
    //  Несмотря на то, что в некоторых заданиях фигурируют слова "синхронный" и "асинхронный" в рамках текущего ДЗ
    //  это всего лишь имитация, реальное переключение между потоками будет рассмотрено на следующем семинаре

    // 1
    requestDataFromServerAsync().subscribe(
        { println("requestDataFromServerAsync: $it") },
        { println("requestDataFromServerAsync error: $it") }
    )

    // 2
    requestServerAsync().subscribe(
        { println("requestServerAsync: complete") },
        { println("requestServerAsync error: $it") }
    )

    // 3
    requestDataFromDbAsync<String>().subscribe(
        { println("requestDataFromDbAsync: $it") },
        { println("requestDataFromDbAsync error: $it") },
        { println("requestDataFromDbAsync: null (complete)") }
    )

    // 4
    emitEachSecond()

    // 5
    xMap { flatMapCompletable(it) }
    xMap { concatMapCompletable(it) }
    xMap { switchMapCompletable(it) }
}

// 1) Какой источник лучше всего подойдёт для запроса на сервер, который возвращает результат?
// Почему?
// The Single class implements the Reactive Pattern for a single value response.
// но т.к. по коду есть вероятность возвращения нулл, то вообще Maybe выглядит предпочтительнее
fun requestDataFromServerAsync(): Single<ByteArray> {

    // Функция имитирует синхронный запрос на сервер, возвращающий результат
    fun getDataFromServerSync(): ByteArray? {
        Thread.sleep(LATENCY);
        val success = Random.nextBoolean()
        return if (success) Random.nextBytes(RESPONSE_LENGTH) else null
    }

    return Single.fromSupplier { getDataFromServerSync() }
}

// 2) Какой источник лучше всего подойдёт для запроса на сервер, который НЕ возвращает результат?
// Почему?
// The Completable class represents a deferred computation without any value but only indication for completion or exception
fun requestServerAsync(): Completable {

    // Функция имитирует синхронный запрос на сервер, не возвращающий результат
    fun getDataFromServerSync() {
        Thread.sleep(LATENCY)
        if (Random.nextBoolean()) throw ServerNotActiveException()
    }

    return Completable.fromAction { getDataFromServerSync() }
}

// 3) Какой источник лучше всего подойдёт для однократного асинхронного возвращения значения из базы данных?
// Почему?
// The Maybe class represents a deferred computation and emission of a single value, no value at all or an exception.
fun <T> requestDataFromDbAsync(): Maybe<T> {

    // Функция имитирует синхронный запрос к БД не возвращающий результата
    fun getDataFromDbSync(): T? {
        Thread.sleep(LATENCY); return null
    }

    return Maybe.fromSupplier { getDataFromDbSync() }
}

// 4) Примените к источнику оператор (несколько операторов), которые приведут к тому, чтобы элемент из источника
// отправлялся раз в секунду (оригинальный источник делает это в 2 раза чаще).
// Значения должны идти последовательно (0, 1, 2 ...)
// Для проверки результата можно использовать .blockingSubscribe(::printer)
fun emitEachSecond() {

    // Источник
    fun source(): Flowable<Long> = Flowable.interval(500, TimeUnit.MILLISECONDS)

    // Принтер
    fun printer(value: Long) = println("${Date()}: value = $value")

// мой вариант
//    source().filter { it % 2 == 0L }
//        .map { it / 2 }
//        .takeWhile { it < 10 }
//        .blockingSubscribe(::printer)

// подсмотрел у коллеги, переделал чтобы значения "проходили" от source
// вроде как .concatMap { Flowable.interval(1000, TimeUnit.MILLISECONDS) } просто вернет новую последовательность, и исходные будут потеряны
    source()
        .onBackpressureBuffer()
        .concatMap { sourceValue ->
            Flowable.timer(1000, TimeUnit.MILLISECONDS)
                .map { sourceValue }
        }
        .takeWhile { it < 10 }
        .blockingSubscribe(::printer)
}

// 5) Функция для изучения разницы между операторами concatMap, flatMap, switchMap
// Нужно вызвать их последовательно и разобраться чем они отличаются
// Документацию в IDEA можно вызвать встав на функцию (например switchMap) курсором и нажав hotkey для вашей раскладки
// Mac: Intellij Idea -> Preferences -> Keymap -> Быстрый поиск "Quick documentation"
// Win, Linux: File -> Settings -> Keymap -> Быстрый поиск "Quick documentation"
//
// конструкция в аргументах функции xMap не имеет значения для ДЗ и создана для удобства вызова функции, чтобы была
//  возможность удобно заменять тип маппинга
//
// Вызов осуществлять поочерёдно из функции main
//
//  xMap { flatMapCompletable(it) }
//  xMap { concatMapCompletable (it) }
//  xMap { switchMapCompletable(it) }
//
fun xMap(mapper: Flowable<Int>.(internalMapper: (Int) -> Completable) -> Completable) {

    fun waitOneSecond() = Completable.timer(1, TimeUnit.SECONDS)

    println("${Date()}: start")
    Flowable.fromIterable(0..20)
        .mapper { iterableIndex ->

            waitOneSecond()
                .doOnComplete { println("${Date()}: finished operation for iterable index $iterableIndex") }

        }
        .blockingSubscribe()
}
