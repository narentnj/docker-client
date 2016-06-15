package de.gesellix.docker.client

import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Specification

@Requires({ LocalDocker.available() })
class NetHttpClientIntegrationSpec extends Specification {

    final static def dockerHubUsername = "gesellix"
    final static def dockerHubPassword = "-yet-another-password-"
    final static def dockerHubEmail = "tobias@gesellix.de"

    def "should allow GET requests"() {
        def client = new NetHttpClient()
        expect:
        client.get("/_ping").content == "OK"
    }

    def "should allow POST requests"() {
        given:
        def client = new NetHttpClient()
        def request = [path : "/images/create",
                       query: [fromImage: "gesellix/docker-client-testimage",
                               tag      : "latest",
                               registry : ""]]

        when:
        def response = client.post(request)
        then:
        response.content.last() == [status: "Status: Image is up to date for gesellix/docker-client-testimage:latest"]
    }

    @IgnoreIf({ dockerHubPassword == "-yet-another-password-" })
    def "should allow POST requests with body"() {
        given:
        def client = new NetHttpClient()
        def authDetails = ["username"     : dockerHubUsername,
                           "password"     : dockerHubPassword,
                           "email"        : dockerHubEmail,
                           "serveraddress": "https://index.docker.io/v1/"]
        def request = [path              : "/auth",
                       body              : authDetails,
                       requestContentType: "application/json"]
        when:
        def response = client.post(request)
        then:
        response.content == [Status: "Login Succeeded"]
    }

    def "should optionally stream a response"() {
        def client = new NetHttpClient()
        def outputStream = new ByteArrayOutputStream()
        when:
        client.get([path  : "/_ping",
                    stdout: outputStream])
        then:
        outputStream.toString() == "OK"
    }

    def "should parse application/json"() {
        def client = new NetHttpClient()
        when:
        def response = client.get("/version")
        then:
        def content = response.content
        content.ApiVersion == "1.23"
        content.Arch == "amd64"
        content.GitCommit == "56888bf"
        content.GoVersion == "go1.5.4"
        content.KernelVersion =~ "\\d.\\d{1,2}.\\d{1,2}(-\\w+)?"
        content.Os == "linux"
        content.Version == "1.11.2"
    }

    @Requires({ LocalDocker.isUnixSocket() })
    def "should support unix socket connections (Linux native or Docker for Mac)"() {
        def client = new NetHttpClient(
                config: new DockerConfig(
                        dockerHost: "unix:///var/run/docker.sock"))
        when:
        def response = client.request([method: "GET",
                                       path  : "/info"])
        then:
        response.status.code == 200
    }

    @Requires({ LocalDocker.isNamedPipe() })
    def "should support named pipe socket connections (Docker for Windows)"() {
        def client = new NetHttpClient(
                config: new DockerConfig(
                        dockerHost: "npipe:////./pipe/docker_engine"))
        when:
        def response = client.request([method: "GET",
                                       path  : "/info"])
        then:
        response.status.code == 200
    }
}