terraform {
  required_version = ">= 1.6.0"
  required_providers { kubernetes = { source="hashicorp/kubernetes", version="~> 2.32" } }
}
variable "namespace" { type=string default="ricozknow" }
resource "kubernetes_namespace" "ricozknow" { metadata { name=var.namespace } }
resource "kubernetes_deployment" "backend" {
 metadata { name="ricozknow" namespace=var.namespace }
 spec {
  replicas=2
  selector { match_labels={app="ricozknow"} }
  template { metadata { labels={app="ricozknow"} } spec { container { name="backend" image="ricozknow/backend:latest" port { container_port=8080 } } } }
 }
}
resource "kubernetes_service" "backend" {
 metadata { name="ricozknow" namespace=var.namespace }
 spec { selector={app="ricozknow"} port { port=8080 target_port=8080 } }
}
