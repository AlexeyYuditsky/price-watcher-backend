package alexeyyuditsky

import io.ktor.server.application.Application
import kotlinx.coroutines.launch
import java.io.File

fun Application.rootModule() {
    configureRouting()

    val yandexMarketClient = YandexMarketClient()

    launch {
        val html = yandexMarketClient.loadProductPage(
            "https://market.yandex.ru/card/fleshka-ultra-dual-drive-go-128gb-usb-32type-c--400mbs/103738676525?do-waremd5=atWL_DvRhYvsnzQfzA54iA&cpc=m4mTN4cd0UZle9ZhqnkYvkYI7LIoekGVDR2P8qRrVSOkZ90kO2swJC2b4hzbPWj3zvARNTg2ZiouZ9m3ToW2c8WKtDeGESre2u4H8HgafBTb2vSZFe86BJCZql-tDasTT25pUETsI04RPp-HdkFWWc1obWN3LReGcB1SDC6oyF5RtZd5xZto-F1ay-qE1d_AUEU6E2oQKjB3GlRcDSH0H9GUT4iolJ59PhDqPnxUTvfiJfaYUzMnC96D9kOF5Uvr3YU5mJyTNJHMrx_H0ozfUedFf0yQIIz5IJrdehu0TEdyyzYskQzc5yFhFMUKfUth2w4BckLy_CIyUet5-XDACLuKu9dWAA-O2wBOAZsRB7A6RT4axSurFOeK2CZqyD_dfeHsC0RD5_DuGcvMM_vu_uOMlwVUIhJB_GJdcMa2n0zIkUpBivmRPQH8Akl3fgyRCnlu5ACJjbcdLxSSkkjeX6j4dAolLAa7PuUsq6JgGZZ3dacEuPmPuQL21D7_RWaEQqh92ssshIo-O_N6JvELh3fhJVwP9GFZ&nid=70046576&ogV=-12"
        )

        File("files/market.html").apply {
            parentFile.mkdirs()
            writeText(html)
        }

        println("market.html saved")
    }
}
