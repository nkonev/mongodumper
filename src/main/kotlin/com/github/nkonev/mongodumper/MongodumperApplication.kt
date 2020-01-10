package com.github.nkonev.mongodumper

import com.mongodb.MongoClient
import com.mongodb.MongoClientURI
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.AutoConfigurationImportSelector
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.PropertySource
import org.springframework.core.annotation.AnnotationAttributes
import org.springframework.core.type.AnnotationMetadata
import org.springframework.data.annotation.Id
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import java.net.URLEncoder
import java.util.*
import javax.servlet.http.HttpServletResponse
import kotlin.collections.ArrayList


@EnableConfigurationProperties(AppProperties::class, BuildProperties::class)
@SpringBootApplication
@PropertySource("classpath:config/git.properties")
@Import(MyCustomSelector::class)
class MongodumperApplication

data class DbConnectionDto(@Id val id: String?, val name: String, val connectionUrl: String)

@Repository
interface DbConnectionRepository : MongoRepository<DbConnectionDto, String>

@ConstructorBinding
@ConfigurationProperties("mongodumper")
data class AppProperties(val mongodump: String, val connections: List<DbConnectionDto>?)

@ConstructorBinding
@ConfigurationProperties("build")
data class BuildProperties(val commitHash: String, val version :String)

data class CheckRequest (val id: String?, val connectionUrl: String?)

data class CheckResponse (val ok: Boolean, val message: String)

data class ConfigResponse (val kiosk: Boolean)

class MyCustomSelector : AutoConfigurationImportSelector() {
	override fun getExclusions(metadata: AnnotationMetadata, attributes: AnnotationAttributes): Set<String> {
		val exclusions = super.getExclusions(metadata, attributes)
		if (System.getProperty("kiosk", "false") == "true" || System.getenv("kiosk") == "true") {
			exclusions.add("org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration")
			exclusions.add("org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration")
		}
		return exclusions
	}
}

@Service
class DbConnectionRepositoryWrapper {
	@Autowired
	private lateinit var repository: ObjectProvider<DbConnectionRepository>

	@Autowired
	private lateinit var operations: ObjectProvider<MongoOperations>

	@Autowired
	private lateinit var appProperties :AppProperties

	private val logger = LoggerFactory.getLogger(javaClass)

	fun save(dto :DbConnectionDto) :DbConnectionDto {
		return repository.getIfAvailable()!!.save(dto)
	}

	fun deleteById(id: String) {
		repository.getIfAvailable()!!.deleteById(id)
	}

	fun findAll(hideUrlInKiosk: Boolean) :List<DbConnectionDto?> {
		val repository = repository.getIfAvailable()
		if (repository!=null) {
			return repository.findAll()
		} else {
			return (if (appProperties.connections == null) ArrayList() else appProperties.connections?.map(mapWithKiosk(hideUrlInKiosk))!!)
		}
	}

	fun findById(id: String, hideUrlInKiosk: Boolean) :Optional<DbConnectionDto> {
		val repository = repository.getIfAvailable()
		if (repository!=null) {
			return repository.findById(id)
		} else {
			return Optional.ofNullable(appProperties.connections?.filter { dbConnectionDto -> dbConnectionDto.id == id }?.map(mapWithKiosk(hideUrlInKiosk))?.firstOrNull())
		}
	}

	private fun mapWithKiosk(hideUrlInKiosk: Boolean) = { dbConnectionDto :DbConnectionDto -> dbConnectionDto.copy(connectionUrl = (if (hideUrlInKiosk) "" else dbConnectionDto.connectionUrl)) }

	fun ensureIndex() {
		val ops = operations.getIfAvailable()
		if (ops != null) {
			logger.info("Ensuring unique index")
			val idx: Index = Index(DbConnectionDto::name.name, Sort.Direction.ASC).unique()
			ops.indexOps(DbConnectionDto::class.java).ensureIndex(idx)
		}
	}

	fun isKiosk() :Boolean {
		return operations.getIfAvailable() == null
	}
}

@RestController
class DatabasesController {

	@Autowired
	private lateinit var repository: DbConnectionRepositoryWrapper

	@Autowired
	private lateinit var appProperties: AppProperties

	private val logger = LoggerFactory.getLogger(javaClass)

	@PostMapping("/db")
	fun create(@RequestBody userDto: DbConnectionDto): DbConnectionDto {
		return repository.save(userDto)
	}

	@PutMapping("/db")
	fun update(@RequestBody userDto: DbConnectionDto): DbConnectionDto {
		return repository.save(userDto)
	}

	@DeleteMapping("/db/{id}")
	fun delete(@PathVariable("id") id: String) {
		repository.deleteById(id)
	}

	@GetMapping("/db")
	fun read(): List<DbConnectionDto?> {
		return repository.findAll(true)
	}

	@GetMapping("/db/{id}")
	fun readById(@PathVariable("id") id: String): Optional<DbConnectionDto> {
		return repository.findById(id, true)
	}

	@GetMapping("/dump/{id}")
	fun dump(@PathVariable("id") id: String, resp: HttpServletResponse) {
		val findById = repository.findById(id, false)
		if (findById.isEmpty) {
			resp.sendError(404, """connection with id '${id}' not found""")
		}
		val dbDto :DbConnectionDto = findById.get()

		val filename = URLEncoder.encode(dbDto.name, "UTF-8")
		resp.status = 200
		resp.setHeader("Content-Type", "application/octet-stream")
		resp.setHeader("Content-Disposition", """attachment; filename*=utf-8''${filename}.gz""")
		val pb :ProcessBuilder = ProcessBuilder(appProperties.mongodump, """--uri=${dbDto.connectionUrl}""", "--gzip", "--archive")
		val process :Process = pb.start()
		val inputStream = process.inputStream
		inputStream.use {
			try {
				inputStream.copyTo(resp.outputStream)
			} catch (e: RuntimeException){
				process.destroyForcibly()
				throw e;
			}
		}
	}

	@PostMapping("/check")
	fun check(@RequestBody checkRequest: CheckRequest) : CheckResponse {
		try {
			var connectionUrl : String? = null
			if (checkRequest.connectionUrl!=null) {
				connectionUrl = checkRequest.connectionUrl
			}
			if (checkRequest.id!=null) {
				connectionUrl = repository.findById(checkRequest.id, false).map { t -> t.connectionUrl }.orElseThrow()
			}
			if (connectionUrl == null) {
				return CheckResponse(false, "Please provide id or connectionUrl")
			}

			val u: MongoClientURI = MongoClientURI(connectionUrl)
			val c: MongoClient = MongoClient(u)
			c.use {
				val dbs = c.listDatabaseNames()
				dbs.forEach({ s: String? -> logger.info("For {} found db {}", checkRequest.connectionUrl, s) })
				return CheckResponse(true, "Ok")
			}
		} catch (e: Throwable) {
			logger.info("Error during check {}", checkRequest.connectionUrl, e)
			return CheckResponse(false, e.message.toString())
		}
	}
}

@RestController
class ConfigController {

	@Autowired
	private lateinit var repository: DbConnectionRepositoryWrapper

	@Autowired
	private lateinit var buildProperties: BuildProperties

	@GetMapping("/version")
	fun version(): BuildProperties {
		return buildProperties;
	}

	@GetMapping("/config")
	fun config() :ConfigResponse {
		return ConfigResponse(repository.isKiosk())
	}
}

@Component
class Commandline : CommandLineRunner {
	private val logger = LoggerFactory.getLogger(javaClass)

	@Autowired
	private lateinit var buildProperties: BuildProperties

	@Autowired
	private lateinit var wrapper: DbConnectionRepositoryWrapper

	override fun run(vararg args: String?) {
		wrapper.ensureIndex()

		logger.info("Git commit hash: {}, version {}", buildProperties.commitHash, buildProperties.version)
	}
}

fun main(args: Array<String>) {
	runApplication<MongodumperApplication>(*args)
}
