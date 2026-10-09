package id.skmnetwork.bukuwarung.domain.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DigitalProductCatalogTest {

    @Test
    fun detectProvider_accuratelyIdentifiesIndonesianMobilePrefixes() {
        assertEquals("TELKOMSEL", DigitalProductCatalog.detectProvider("08123456789"))
        assertEquals("TELKOMSEL", DigitalProductCatalog.detectProvider("+6285211112222"))
        assertEquals("INDOSAT", DigitalProductCatalog.detectProvider("085712345678"))
        assertEquals("XL", DigitalProductCatalog.detectProvider("087812345678"))
        assertEquals("AXIS", DigitalProductCatalog.detectProvider("083812345678"))
        assertEquals("TRI", DigitalProductCatalog.detectProvider("089612345678"))
        assertEquals("SMARTFREN", DigitalProductCatalog.detectProvider("088812345678"))
        assertEquals("PLN", DigitalProductCatalog.detectProvider("142345678901"))
        assertNull(DigitalProductCatalog.detectProvider("0800"))
        assertNull(DigitalProductCatalog.detectProvider("123"))
    }
}
