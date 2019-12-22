package com.github.nkonev.mongodumper

import io.github.bonigarcia.wdm.WebDriverManager
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.FindBy
import org.openqa.selenium.support.PageFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.TimeUnit


class DatabasesPage(private val driver: WebDriver, private val port :Integer) {

	init {
		PageFactory.initElements(driver, this)
	}

	private val pageUrl = "http://localhost:${port}"

	fun open() = driver.get(pageUrl)

	@FindBy(css = ".App pre")
	lateinit var headerText: WebElement

	fun verifyContent() {
		assertThat("Should display header", headerText.text.contains("mongorestore --drop", false));
	}

}

@TestPropertySource(properties = [
	"server.port=7077"
])
@SpringBootTest(classes=[MongodumperApplication::class], webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Testcontainers
@AutoConfigureMockMvc
class MongodumperApplicationTests {

	@LocalServerPort
	lateinit var port :Integer

	@Autowired
	lateinit var databasesRepository: DbConnectionRepository

	@Autowired
	lateinit var mockMvc: MockMvc

	companion object {
		val mongoPort = 27017
		// https://sites.google.com/a/chromium.org/chromedriver/
		val chromedriverVersion = "79.0.3945.36"
		val mongoProperty = "spring.data.mongodb.uri"

		@Container
		var mongoContainer : GenericContainer<*> = GenericContainer<Nothing>("mongo:4.2.0-bionic").withExposedPorts(mongoPort);

		lateinit var driver: WebDriver

		@JvmStatic
		@BeforeAll
		fun beforeAll() {
			val mappedPort = mongoContainer.getMappedPort(mongoPort)
			System.setProperty(mongoProperty, "mongodb://localhost:${mappedPort}/mongodumper")

			WebDriverManager.chromedriver().version(chromedriverVersion).setup();

			// https://developers.google.com/web/updates/2017/04/headless-chrome
			val chromeOptions = ChromeOptions()
			chromeOptions.addArguments("--headless", "--verbose", "--no-sandbox", "--disable-dev-shm-usage", "--whitelisted-ips=\"\"")
            println("Starting chrome driver from java")
            driver = ChromeDriver(chromeOptions)

			driver.manage()?.timeouts()?.implicitlyWait(30, TimeUnit.SECONDS)
			driver.manage()?.window()?.maximize()
		}

		@JvmStatic
		@AfterAll
		fun afterMethod() {
			driver.close()
		}

	}

	@Test
	fun `Should display header`() {

		val profilePage = DatabasesPage(driver, port)

		profilePage.run {
			open()
		}

		profilePage.verifyContent()
	}

	@Test
	fun `Created db is displayed`() {
		Assert.assertTrue(databasesRepository.count() == 0L)

		val selfMongoUrl = System.getProperty(mongoProperty)
		mockMvc.perform(MockMvcRequestBuilders
				.post("/db")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
			{
			"name": "My first connection",
			"connectionUrl": "${selfMongoUrl}"
			}
		"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").exists())

		mockMvc.perform(MockMvcRequestBuilders.get("/db"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("My first connection"))
	}
}
