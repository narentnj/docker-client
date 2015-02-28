package de.gesellix.docker.client

import de.gesellix.docker.client.protocolhandler.DockerContentHandlerFactory
import de.gesellix.docker.client.protocolhandler.DockerURLHandler
import de.gesellix.docker.client.protocolhandler.RawInputStream
import de.gesellix.socketfactory.https.KeyStoreUtil
import org.apache.commons.io.IOUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

import static de.gesellix.socketfactory.https.KeyStoreUtil.getKEY_STORE_PASSWORD
import static javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm

// Proof-of-concept for https://docs.docker.com/reference/api/docker_remote_api_v1.17/#attach-to-a-container
class LowLevelDockerClient {

  def Logger logger = LoggerFactory.getLogger(LowLevelDockerClient)

  ContentHandlerFactory contentHandlerFactory = new DockerContentHandlerFactory()

  def dockerHost = "http://127.0.0.1:2375"
  URL dockerHostUrl

  def sslSocketFactory = null

  def getDockerBaseUrl() {
    if (!getDockerHostUrl()) {
      dockerHostUrl = new DockerURLHandler(dockerHost: getDockerHost()).getURL()
    }
    return dockerHostUrl
  }

  def get(requestConfig) {
    def config = ensureValidRequestConfig(requestConfig)
    config.method = "GET"
    return request(config)
  }

  def post(requestConfig) {
    def config = ensureValidRequestConfig(requestConfig)
    config.method = "POST"
    return request(config)
  }

  def request(config) {
    if (!config || config == [:]) {
      throw new IllegalArgumentException("expected a valid request config object")
    }
    config.query = (config.query) ? "?${queryToString(config.query)}" : ""
    def requestUrl = new URL("${getDockerBaseUrl()}${config.path}${config.query}")
    logger.info("${config.method} ${requestUrl}")

    def connection = connect(config.method, requestUrl)
//    connection.setDoOutput(true)
    // since we listen to a stream we disable the timeout
//    connection.setConnectTimeout(0)
    // since we listen to a stream we disable the timeout
//    connection.setReadTimeout(0)
//    connection.setUseCaches(false)

    def statusLine = connection.headerFields[null]
    logger.debug("status: ${statusLine}")

    def headers = connection.headerFields.findAll { key, value ->
      key != null
    }.collectEntries { key, value ->
      [key.toLowerCase(), value]
    }
    String contentType = headers['content-type']?.first()
    logger.debug("header: ${headers}")

    logger.debug("content-length: ${headers['content-length']}")
    logger.debug("content-type: ${contentType}")

    def response = [
        statusLine: [
            text: statusLine,
            code: connection.responseCode
        ],
        headers   : headers,
        stream    : connection.inputStream
    ]

    def mimetype = contentType.replace(" ", "").split(";").first()
    def contentHandler = contentHandlerFactory.createContentHandler(mimetype)
    if (contentHandler == null) {
      logger.warn("couldn't find a specific ContentHandler for '${contentType}'.")
      IOUtils.copy(response.stream, System.out)
      println()
    }
    else {
      switch (mimetype) {
        case "application/vnd.docker.raw-stream":
          InputStream rawStream = contentHandler.getContent(connection) as RawInputStream
          if (config.outputStream) {
            IOUtils.copy(rawStream as InputStream, config.outputStream as OutputStream)
          }
          else {
            IOUtils.copy(rawStream, System.out)
            println()
            response.stream = null
          }
          break;
        case "application/json":
          def body = contentHandler.getContent(connection)
          if (config.outputStream && body instanceof InputStream) {
            IOUtils.copy(body as InputStream, config.outputStream as OutputStream)
          }
          else {
            if (body instanceof InputStream) {
              response.content = IOUtils.toString(body as InputStream)
              response.stream = null
            }
            else {
              response.content = body
              response.stream = null
            }
          }
          break;
        case "text/html":
          def body = contentHandler.getContent(connection)
          if (config.outputStream && body instanceof InputStream) {
            IOUtils.copy(body as InputStream, config.outputStream as OutputStream)
          }
          else {
            response.content = IOUtils.toString(body as InputStream)
            response.stream = null
          }
          break;
        case "text/plain":
          def body = contentHandler.getContent(connection)
          if (config.outputStream && body instanceof InputStream) {
            IOUtils.copy(body as InputStream, config.outputStream as OutputStream)
          }
          else {
            response.content = IOUtils.toString(body as InputStream)
            response.stream = null
          }
          break;
        default:
          if (config.outputStream) {
            IOUtils.copy(response.stream as InputStream, config.outputStream as OutputStream)
          }
          else {
            response.content = IOUtils.toString(response.stream as InputStream)
            response.stream = null
          }
          println()
          break
      }
    }

    return response
  }

  def ensureValidRequestConfig(config) {
    def validConfig = config
    if (config instanceof String) {
      validConfig = [path: config]
    }
    if (!validConfig.path) {
      throw new IllegalArgumentException("need a path")
    }
    return validConfig
  }

  def queryToString(query) {
//    Charset.forName("UTF-8")
    def queryAsString = query.collect { key, value ->
      "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
    }
    return queryAsString.join("&")
  }

  def connect(method, URL requestUrl) {
    def connection = requestUrl.openConnection()
    connection.setRequestMethod(method)

    def dockerCertPath = System.getProperty("docker.cert.path", System.env.DOCKER_CERT_PATH)
    if (connection instanceof HttpsURLConnection) {
      if (!sslSocketFactory) {
        def keyStore = KeyStoreUtil.createDockerKeyStore(new File(dockerCertPath).absolutePath)
        final KeyManagerFactory kmfactory = KeyManagerFactory.getInstance(getDefaultAlgorithm());
        kmfactory.init(keyStore, KEY_STORE_PASSWORD as char[]);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(getDefaultAlgorithm());
        tmf.init(keyStore)
        def sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmfactory.keyManagers, tmf.trustManagers, null)
        sslSocketFactory = sslContext.socketFactory
      }
      ((HttpsURLConnection) connection).setSSLSocketFactory(sslSocketFactory)
    }

    return connection
  }

  def getCharset(contentTypeHeader) {
    String charset = "utf-8"
    for (String param : contentTypeHeader.replace(" ", "").split(";")) {
      if (param.startsWith("charset=")) {
        charset = param.split("=", 2)[1]
        break
      }
    }
    logger.info("charset: ${charset}")
    return charset
  }
}