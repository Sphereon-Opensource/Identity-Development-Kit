variable "OIDF_SUITE_REPOSITORY" {
  default = "https://github.com/openid-certification/conformance-suite.git"
}

variable "OIDF_SUITE_COMMIT" {
  default = "9fa849f5de85b581707f929508becc14534f183a"
}

variable "GIT_IMAGE" {
  default = "alpine/git:2.47.2@sha256:062a01ad7a0eb17cff382bc5e26086b4d710e56dfdfdf001109a49b6d9bd378c"
}

variable "MAVEN_IMAGE" {
  default = "maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237"
}

variable "SERVER_RUNTIME_IMAGE" {
  default = "eclipse-temurin:21-jre@sha256:273396ed5998598ed1091e8d72711c2d36980a0e65103859c55a4e977a41ffd3"
}

variable "UPSTREAM_NGINX_IMAGE" {
  default = "registry.gitlab.com/openid/conformance-suite/nginx:release-v5.1.43@sha256:6a13e1c9ae2d19f2d0fea0a371905d580d61b960e0da84c01d5006729a5f1426"
}

variable "SERVER_IMAGE" {
  default = "sphereon/oidf-conformance-suite-vdx:9fa849f5-vdx.10"
}

variable "NGINX_IMAGE" {
  default = "sphereon/oidf-conformance-suite-nginx-vdx:9fa849f5-vdx.10"
}

group "default" {
  targets = ["server", "nginx"]
}

target "server" {
  context    = "."
  dockerfile = "Dockerfile.server"
  tags       = [SERVER_IMAGE]
  args = {
    OIDF_SUITE_REPOSITORY = OIDF_SUITE_REPOSITORY
    OIDF_SUITE_COMMIT     = OIDF_SUITE_COMMIT
    GIT_IMAGE             = GIT_IMAGE
    MAVEN_IMAGE           = MAVEN_IMAGE
    RUNTIME_IMAGE         = SERVER_RUNTIME_IMAGE
  }
}

target "nginx" {
  context    = "."
  dockerfile = "Dockerfile.nginx"
  tags       = [NGINX_IMAGE]
  args = {
    RUNTIME_IMAGE = UPSTREAM_NGINX_IMAGE
  }
}
