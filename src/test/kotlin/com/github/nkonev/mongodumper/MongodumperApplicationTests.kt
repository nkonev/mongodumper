package com.github.nkonev.mongodumper

import com.github.nkonev.mongodumper.MongodumperApplicationTests.Companion.appPort
import com.github.nkonev.mongodumper.MongodumperApplicationTests.Companion.webdriverContainer
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.FindBy
import org.openqa.selenium.support.PageFactory
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
import org.testcontainers.containers.BrowserWebDriverContainer
import org.testcontainers.containers.BrowserWebDriverContainer.VncRecordingMode.RECORD_ALL
import org.testcontainers.containers.DefaultRecordingFileFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.lifecycle.TestDescription
import org.testcontainers.utility.MountableFile
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit


class DatabasesPage(private val driver: WebDriver, private val host: String, private val port :Integer) {

	init {
		refreshElements()
	}

	fun refreshElements() {
		PageFactory.initElements(driver, this)
	}

	private val pageUrl = "http://${host}:${port}"

	fun open() = driver.get(pageUrl)

	@FindBy(css = ".App .header-text")
	lateinit var headerText: WebElement

	@FindBy(css = "button .fab-add")
	lateinit var addButton: WebElement

	@FindBy(css = ".edit-modal")
	lateinit var editModal: WebElement

	@FindBy(css = ".list-db-connections .downloadable-clickable")
	lateinit var connections: java.util.List<WebElement>

	class EditModal(private val driver: WebDriver) {
		init {
			PageFactory.initElements(driver, this)
		}

		@FindBy(css = ".edit-modal button.edit-modal-save")
		lateinit var saveButton: WebElement

		@FindBy(css = ".edit-modal button.edit-modal-cancel")
		lateinit var cancelButton: WebElement

		@FindBy(css = ".edit-modal .edit-modal-name input")
		lateinit var name: WebElement

		@FindBy(css = ".edit-modal .edit-modal-connection-url input")
		lateinit var connectionUrl: WebElement

		fun input(newName: String, newConnectionUrl: String) {

			val webDriverWait0 = WebDriverWait(driver, 10, 500)
			webDriverWait0.until(ExpectedConditions.visibilityOf(name)).sendKeys(newName)

			val webDriverWait1 = WebDriverWait(driver, 10, 500)
			webDriverWait1.until(ExpectedConditions.visibilityOf(connectionUrl)).sendKeys(newConnectionUrl)
		}

		fun save() {
			val webDriverWait0 = WebDriverWait(driver, 10, 500)
			webDriverWait0.until(ExpectedConditions.elementToBeClickable(saveButton)).click()
		}
	}


	fun verifyContent() {
		val webDriverWait = WebDriverWait(driver, 10, 500)
		webDriverWait.until(ExpectedConditions.visibilityOf(headerText))
		assertThat("Should display header", headerText.text.contains("mongorestore --drop", false));
	}

	fun openNewModal() : EditModal {
		addButton.click()

		val webDriverWait = WebDriverWait(driver, 10, 500)
		webDriverWait.until(ExpectedConditions.visibilityOf(editModal))

		return EditModal(driver)
	}

	fun getDatabasesList(): List<String> {
		//refreshElements()
		return connections.map { webElement -> webElement.text }
	}

	fun clickToConnection(index: Int){
		connections.get(index).click()
	}
}

class KotlinWebDriverContainer : BrowserWebDriverContainer<KotlinWebDriverContainer>()

class TestcontainersCallbackPusherExtension : TestWatcher {
	fun getDescription(context: ExtensionContext?) : TestDescription {
		return object: TestDescription {
			override fun getTestId(): String {
				return ""
			}

			override fun getFilesystemFriendlyName(): String? {
				return ""+ context?.testClass?.get()?.name + "_" + context?.testMethod?.get()?.name
			}
		}
	}

    override fun testFailed(context: ExtensionContext?, cause: Throwable?) {
		webdriverContainer.afterTest(getDescription(context), Optional.ofNullable(cause))
    }

    override fun testSuccessful(context: ExtensionContext?) {
		webdriverContainer.afterTest(getDescription(context), Optional.empty())
    }
}

@TestPropertySource(properties = [
	"server.port=$appPort"
])
@SpringBootTest(classes=[MongodumperApplication::class], webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Testcontainers
@AutoConfigureMockMvc
@ExtendWith(TestcontainersCallbackPusherExtension::class)
class MongodumperApplicationTests {

	@LocalServerPort
	lateinit var port :Integer

	@Autowired
	lateinit var databasesRepository: DbConnectionRepository

	@Autowired
	lateinit var mockMvc: MockMvc

	companion object {
		val logger :Logger = LoggerFactory.getLogger(MongodumperApplicationTests::class.java)

		val inContainerDownloadedDir = "/tmp/chrome-downloaded"
		val inHostResultsRootDir = "./build"
		val inHostResultsDownloadedDir = inHostResultsRootDir+"/chrome-downloaded"

		const val appPort = 7077
		val mongoPort = 27017

		val mongoProperty = "spring.data.mongodb.uri"
		val mongoContainerVersion = "mongo:4.2.2-bionic"

		val host = "host.testcontainers.internal"

		lateinit var webdriverContainer : KotlinWebDriverContainer

		lateinit var mongoContainer : GenericContainer<*>

		lateinit var driver: WebDriver

		fun getHostForBrowser() : String{
			return host
		}

		@JvmStatic
		@BeforeAll
		fun beforeAll() {
			org.testcontainers.Testcontainers.exposeHostPorts(appPort);

			val videoDir = File(inHostResultsRootDir+"/video")
			videoDir.deleteRecursively()
			videoDir.mkdirs()

			val downloadedDir = File(inHostResultsDownloadedDir)
			downloadedDir.deleteRecursively()
			downloadedDir.mkdirs()

			val chromePrefs :HashMap<String, Any> = HashMap<String, Any>()
			chromePrefs.put("download.default_directory", inContainerDownloadedDir)
			chromePrefs.put("download.prompt_for_download", false)
			chromePrefs.put("download.directory_upgrade", true)

			val chromeOptions = ChromeOptions()
			chromeOptions.setExperimentalOption("prefs", chromePrefs)
			webdriverContainer = KotlinWebDriverContainer()
					.withCapabilities(chromeOptions)
					.withRecordingMode(RECORD_ALL, videoDir)
					.withRecordingFileFactory(DefaultRecordingFileFactory())
			webdriverContainer.start()

			logger.info("Use this VNC address for connect into container with remmina. Select max color depth and quality. If any errors, check remmina's console window.")
			logger.debug("VNC address " + webdriverContainer.vncAddress)

			mongoContainer = GenericContainer<Nothing>(mongoContainerVersion).withExposedPorts(mongoPort)
			mongoContainer.start()


			val mappedPort = mongoContainer.getMappedPort(mongoPort)
			System.setProperty(mongoProperty, "mongodb://localhost:${mappedPort}/mongodumper")

			driver = webdriverContainer.getWebDriver();

			driver.manage()?.timeouts()?.implicitlyWait(30, TimeUnit.SECONDS)
			driver.manage()?.window()?.maximize()
		}

		@JvmStatic
		@AfterAll
		fun afterMethod() {
			driver.close()

			webdriverContainer.close()
		}

	}

	@BeforeEach
	fun setup() {
		databasesRepository.deleteAll()
	}

	@Test
	fun `Should display header`() {

		val databasesPage = DatabasesPage(driver, getHostForBrowser(), port)

		databasesPage.run {
			open()
		}

		databasesPage.verifyContent()
	}

	@Test
	fun `Created db is displayed REST`() {
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

	@Test
	fun `Created db is displayed Integration`() {
		Assert.assertTrue(databasesRepository.count() == 0L)

		val selfMongoUrl = System.getProperty(mongoProperty)

		val databasesPage = DatabasesPage(driver, getHostForBrowser(), port)
		val newConnectionName = "new connection name"

		databasesPage.run {
			open()
			val modal = openNewModal()
            modal.run {
                input(newConnectionName, selfMongoUrl)
                save()
            }
			waitForCondition(10) {
				Assert.assertTrue(getDatabasesList().contains(newConnectionName))
			}
		}

		Assert.assertEquals(1, databasesRepository.count())
		val find = databasesRepository.findAll().get(0)
		Assert.assertTrue(find.connectionUrl == selfMongoUrl)

		databasesPage.clickToConnection(0)

		TimeUnit.SECONDS.sleep(5)
		val filename = newConnectionName+".gz"

		val inContainerFullFilePath = inContainerDownloadedDir+"/"+filename
		val inHostFullFilePath = inHostResultsDownloadedDir+"/"+filename
		webdriverContainer.copyFileFromContainer(inContainerFullFilePath, inHostFullFilePath)

		mongoContainer.copyFileToContainer(MountableFile.forHostPath(inHostFullFilePath), inContainerFullFilePath)
		val execInContainerResult = mongoContainer.execInContainer("mongorestore", "--drop", "--gzip", "--archive=" + inContainerFullFilePath)
		logger.info(execInContainerResult.toString())
		Assert.assertEquals(0, execInContainerResult.exitCode)
	}

	fun waitForCondition(secondsWait: Int, f: ()-> Unit){
		var success = false
		for (i in 1..secondsWait step 1) {
			try {
				f()
				success = true
				break
			} catch (e: Throwable) {
				logger.info("Retrying..., lastError=" + e.message)
				TimeUnit.SECONDS.sleep(1)
			}
		}
		Assert.assertTrue(success)
	}

}
