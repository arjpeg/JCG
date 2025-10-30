import play.api.libs.json._
    
// serializing boundary methods
case class JsonMethod(
name: String,
declaringClass: String,
returnType: String,
parameterTypes: Seq[String]
)

case class JsonTarget(
    method: JsonMethod,
    reachable: Int,
    not_covered: Int
)

case class JsonCallSite(
    line: Int,
    pc: Option[Int],
    targets: Seq[JsonTarget]
)

case class JsonBoundaryMethod(
    caller: JsonMethod,
    callSites: Seq[JsonCallSite]
)

case class JsonOutput(boundary_methods: Seq[JsonBoundaryMethod])


object JsonFormats {
    implicit val methodFormat: OFormat[JsonMethod] = Json.format[JsonMethod]
    implicit val targetFormat: OFormat[JsonTarget] = Json.format[JsonTarget]
    implicit val callSiteFormat: OFormat[JsonCallSite] = Json.format[JsonCallSite]
    implicit val boundaryFormat: OFormat[JsonBoundaryMethod] = Json.format[JsonBoundaryMethod]
    implicit val outputFormat: OFormat[JsonOutput] = Json.format[JsonOutput]
}