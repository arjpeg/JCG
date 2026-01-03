import java.io.File
import java.io.FileInputStream
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

import play.api.libs.json._
import scala.collection.mutable

/**
 * Calculates boundary coverage by comparing boundary methods with dynamic call graph.
 * 
 * @author Generated for cgFuzz pipeline
 */
object CalculateBoundaryCoverage {

    def main(args: Array[String]): Unit = {
        var dynamicCGPath = ""
        var boundaryPath = ""
        var distancePath = ""
        var outputJsonPath = ""
        var outputReportPath = ""

        // Parse command line arguments
        var i = 0
        while (i < args.length) {
            args(i) match {
                case "--dynamic-cg" =>
                    dynamicCGPath = args(i + 1)
                    i += 2
                case "--boundary-methods" =>
                    boundaryPath = args(i + 1)
                    i += 2
                case "--distances" =>
                    distancePath = args(i + 1)
                    i += 2
                case "--output-json" =>
                    outputJsonPath = args(i + 1)
                    i += 2
                case "--output-report" =>
                    outputReportPath = args(i + 1)
                    i += 2
                case _ =>
                    println(s"Unknown argument: ${args(i)}")
                    i += 1
            }
        }

        // Validate inputs
        require(dynamicCGPath.nonEmpty, "Missing --dynamic-cg argument")
        require(boundaryPath.nonEmpty, "Missing --boundary-methods argument")
        require(outputJsonPath.nonEmpty, "Missing --output-json argument")
        require(outputReportPath.nonEmpty, "Missing --output-report argument")

        // Read input files
        println(s"Reading dynamic call graph from: $dynamicCGPath")
        val dynamicCG = readCG(new File(dynamicCGPath)).toMap

        println(s"Reading boundary methods from: $boundaryPath")
        val boundaryMethods = readBoundaryMethods(new File(boundaryPath))

        println(s"Calculating boundary coverage...")
        val coverageResult = calculateCoverage(dynamicCG, boundaryMethods)

        // Load distances if provided
        val distances = if (distancePath.nonEmpty && new File(distancePath).exists()) {
            Some(readDistances(new File(distancePath)))
        } else {
            None
        }

        // Write outputs
        println(s"Writing JSON output to: $outputJsonPath")
        writeJsonOutput(coverageResult, distances, outputJsonPath)

        println(s"Writing report to: $outputReportPath")
        writeReport(coverageResult, distances, outputReportPath)

        println("Boundary coverage calculation completed!")
    }

    /**
     * Read a call graph from file (supports .json, .zip, .gz formats)
     */
    def readCG(cgFile: File): ReachableMethods = {
        val input =
            if (cgFile.getName.endsWith(".zip") || cgFile.getName.endsWith(".gz"))
                new GZIPInputStream(new FileInputStream(cgFile))
            else
                new FileInputStream(cgFile)

        Json.parse(input).validate[ReachableMethods].get
    }

    /**
     * Read boundary methods from JSON file
     */
    def readBoundaryMethods(file: File): JsonOutput = {
        val source = scala.io.Source.fromFile(file)
        try {
            val jsonString = source.mkString
            Json.parse(jsonString).validate[JsonOutput].get
        } finally {
            source.close()
        }
    }

    /**
     * Read distance data from JSON file
     */
    def readDistances(file: File): Map[String, Double] = {
        val source = scala.io.Source.fromFile(file)
        try {
            val jsonString = source.mkString
            val json = Json.parse(jsonString)
            // Assuming distances are stored as {"method_signature": distance_value}
            json.as[Map[String, Double]]
        } finally {
            source.close()
        }
    }

    /**
     * Convert JsonMethod to Method for comparison
     */
    def jsonMethodToMethod(jm: JsonMethod): Method = {
        Method(jm.name, jm.declaringClass, jm.returnType, jm.parameterTypes.toList)
    }

    /**
     * Check if a method exists in the call graph
     */
    def isMethodCovered(method: Method, cg: Map[Method, Set[CallSite]]): Boolean = {
        cg.contains(method)
    }

    /**
     * Check if a call site with specific targets is covered in the dynamic CG
     */
    def isCallSiteCovered(
        caller: Method,
        callSite: JsonCallSite,
        dynamicCG: Map[Method, Set[CallSite]]
    ): (Boolean, Int, Int) = {
        val dynamicCallSites = dynamicCG.get(caller)
        
        if (dynamicCallSites.isEmpty) {
            // Caller method not in dynamic CG, nothing is covered
            val totalTargets = callSite.targets.map(_.method).size
            return (false, 0, totalTargets)
        }

        var coveredTargets = 0
        var totalTargets = 0

        callSite.targets.foreach { jsonTarget =>
            totalTargets += 1
            val targetMethod = jsonMethodToMethod(jsonTarget.method)
            
            // Find matching call sites in dynamic CG
            val matchingCS = dynamicCallSites.get.find { cs =>
                cs.line == callSite.line && 
                (callSite.pc.isEmpty || cs.pc == callSite.pc) &&
                cs.targets.contains(targetMethod)
            }

            if (matchingCS.isDefined) {
                coveredTargets += 1
            }
        }

        val isCovered = coveredTargets > 0
        (isCovered, coveredTargets, totalTargets)
    }

    /**
     * Calculate coverage statistics for all boundary methods
     */
    def isEdgeCovered(
        caller: Method,
        callSiteLine: Int,
        callSitePC: Option[Int],
        targetMethod: Method,
        dynamicCG: Map[Method, Set[CallSite]]
    ): Boolean = {
        val dynamicCallSites = dynamicCG.get(caller)
        
        if (dynamicCallSites.isEmpty) {
            return false
        }

        // Find a call site in dynamic CG that matches line, pc, and contains the target
        dynamicCallSites.get.exists { cs =>
            cs.line == callSiteLine && 
            cs.pc == callSitePC &&
            cs.targets.contains(targetMethod)
        }
    }

    /**
    * Calculate coverage statistics for all boundary methods
    */
    def calculateCoverage(
        dynamicCG: Map[Method, Set[CallSite]],
        boundaryMethods: JsonOutput
    ): CoverageResult = {
        var totalBoundaryMethods = 0
        var coveredBoundaryMethods = 0
        var totalCallSites = 0
        var coveredCallSites = 0
        var totalEdges = 0
        var coveredEdges = 0

        val methodDetails = mutable.ListBuffer[MethodCoverageDetail]()

        boundaryMethods.boundary_methods.foreach { boundaryMethod =>
            totalBoundaryMethods += 1
            val callerMethod = jsonMethodToMethod(boundaryMethod.caller)
            
            var methodHasAnyCoveredEdge = false
            var methodCallSitesCovered = 0
            var methodTotalCallSites = 0
            var methodEdgesCovered = 0
            var methodTotalEdges = 0

            val callSiteDetails = mutable.ListBuffer[CallSiteCoverageDetail]()

            boundaryMethod.callSites.foreach { callSite =>
                methodTotalCallSites += 1
                totalCallSites += 1

                var callSiteHasAnyCoveredEdge = false
                var callSiteCoveredEdges = 0
                val callSiteTotalEdges = callSite.targets.length

                methodTotalEdges += callSiteTotalEdges
                totalEdges += callSiteTotalEdges

                // Check each edge (caller -> target) at this call site
                callSite.targets.foreach { jsonTarget =>
                    val targetMethod = jsonMethodToMethod(jsonTarget.method)
                    
                    val edgeCovered = isEdgeCovered(
                        callerMethod,
                        callSite.line,
                        callSite.pc,
                        targetMethod,
                        dynamicCG
                    )

                    if (edgeCovered) {
                        callSiteCoveredEdges += 1
                        methodEdgesCovered += 1
                        coveredEdges += 1
                        callSiteHasAnyCoveredEdge = true
                        methodHasAnyCoveredEdge = true
                    }
                }

                if (callSiteHasAnyCoveredEdge) {
                    methodCallSitesCovered += 1
                    coveredCallSites += 1
                }

                callSiteDetails += CallSiteCoverageDetail(
                    line = callSite.line,
                    pc = callSite.pc,
                    totalTargets = callSiteTotalEdges,
                    coveredTargets = callSiteCoveredEdges,
                    covered = callSiteHasAnyCoveredEdge
                )
            }

            if (methodHasAnyCoveredEdge) {
                coveredBoundaryMethods += 1
            }

            methodDetails += MethodCoverageDetail(
                method = boundaryMethod.caller,
                covered = methodHasAnyCoveredEdge,
                totalCallSites = methodTotalCallSites,
                coveredCallSites = methodCallSitesCovered,
                totalEdges = methodTotalEdges,
                coveredEdges = methodEdgesCovered,
                callSites = callSiteDetails.toList
            )
        }

        CoverageResult(
            totalBoundaryMethods = totalBoundaryMethods,
            coveredBoundaryMethods = coveredBoundaryMethods,
            totalCallSites = totalCallSites,
            coveredCallSites = coveredCallSites,
            totalEdges = totalEdges,
            coveredEdges = coveredEdges,
            methodDetails = methodDetails.toList
        )
    }

    /**
     * Write coverage results to JSON file
     */
    def writeJsonOutput(
        result: CoverageResult,
        distances: Option[Map[String, Double]],
        outputPath: String
    ): Unit = {
        val json = Json.obj(
            "summary" -> Json.obj(
                "total_boundary_methods" -> result.totalBoundaryMethods,
                "covered_boundary_methods" -> result.coveredBoundaryMethods,
                "method_coverage_percentage" -> (if (result.totalBoundaryMethods > 0) 
                    (result.coveredBoundaryMethods.toDouble / result.totalBoundaryMethods * 100) 
                    else 0.0),
                "total_call_sites" -> result.totalCallSites,
                "covered_call_sites" -> result.coveredCallSites,
                "call_site_coverage_percentage" -> (if (result.totalCallSites > 0)
                    (result.coveredCallSites.toDouble / result.totalCallSites * 100)
                    else 0.0),
                "total_edges" -> result.totalEdges,
                "covered_edges" -> result.coveredEdges,
                "edge_coverage_percentage" -> (if (result.totalEdges > 0)
                    (result.coveredEdges.toDouble / result.totalEdges * 100)
                    else 0.0)
            ),
            "method_details" -> result.methodDetails.map { detail =>
                Json.obj(
                    "method" -> Json.obj(
                        "name" -> detail.method.name,
                        "declaringClass" -> detail.method.declaringClass,
                        "returnType" -> detail.method.returnType,
                        "parameterTypes" -> detail.method.parameterTypes
                    ),
                    "covered" -> detail.covered,
                    "total_call_sites" -> detail.totalCallSites,
                    "covered_call_sites" -> detail.coveredCallSites,
                    "total_edges" -> detail.totalEdges,
                    "covered_edges" -> detail.coveredEdges
                )
            }
        )

        Files.write(
            Paths.get(outputPath),
            Json.prettyPrint(json).getBytes(StandardCharsets.UTF_8)
        )
    }

    /**
     * Write human-readable coverage report
     */
    def writeReport(
        result: CoverageResult,
        distances: Option[Map[String, Double]],
        outputPath: String
    ): Unit = {
        val report = new StringBuilder

        report.append("=" * 80 + "\n")
        report.append("BOUNDARY COVERAGE REPORT\n")
        report.append("=" * 80 + "\n\n")

        report.append("SUMMARY\n")
        report.append("-" * 80 + "\n")
        report.append(f"Total Boundary Methods:    ${result.totalBoundaryMethods}%6d\n")
        report.append(f"Covered Boundary Methods:  ${result.coveredBoundaryMethods}%6d\n")
        val methodCovPct = if (result.totalBoundaryMethods > 0) 
            (result.coveredBoundaryMethods.toDouble / result.totalBoundaryMethods * 100) 
            else 0.0
        report.append(f"Method Coverage:           $methodCovPct%6.2f%%\n\n")

        report.append(f"Total Call Sites:          ${result.totalCallSites}%6d\n")
        report.append(f"Covered Call Sites:        ${result.coveredCallSites}%6d\n")
        val csCovPct = if (result.totalCallSites > 0)
            (result.coveredCallSites.toDouble / result.totalCallSites * 100)
            else 0.0
        report.append(f"Call Site Coverage:        $csCovPct%6.2f%%\n\n")

        report.append(f"Total Edges:               ${result.totalEdges}%6d\n")
        report.append(f"Covered Edges:             ${result.coveredEdges}%6d\n")
        val edgeCovPct = if (result.totalEdges > 0)
            (result.coveredEdges.toDouble / result.totalEdges * 100)
            else 0.0
        report.append(f"Edge Coverage:             $edgeCovPct%6.2f%%\n\n")

        report.append("=" * 80 + "\n\n")

        report.append("COVERED METHODS\n")
        report.append("-" * 80 + "\n")
        result.methodDetails.filter(_.covered).foreach { detail =>
            report.append(s"✓ ${detail.method.declaringClass}.${detail.method.name}\n")
            report.append(f"  Call Sites: ${detail.coveredCallSites}%d/${detail.totalCallSites}%d  ")
            report.append(f"Edges: ${detail.coveredEdges}%d/${detail.totalEdges}%d\n")
        }

        report.append("\n")
        report.append("UNCOVERED METHODS\n")
        report.append("-" * 80 + "\n")
        result.methodDetails.filterNot(_.covered).foreach { detail =>
            report.append(s"✗ ${detail.method.declaringClass}.${detail.method.name}\n")
            report.append(f"  Call Sites: ${detail.coveredCallSites}%d/${detail.totalCallSites}%d  ")
            report.append(f"Edges: ${detail.coveredEdges}%d/${detail.totalEdges}%d\n")
        }

        Files.write(
            Paths.get(outputPath),
            report.toString.getBytes(StandardCharsets.UTF_8)
        )
    }

    // Data structures for coverage results
    case class CallSiteCoverageDetail(
        line: Int,
        pc: Option[Int],
        totalTargets: Int,
        coveredTargets: Int,
        covered: Boolean
    )

    case class MethodCoverageDetail(
        method: JsonMethod,
        covered: Boolean,
        totalCallSites: Int,
        coveredCallSites: Int,
        totalEdges: Int,
        coveredEdges: Int,
        callSites: List[CallSiteCoverageDetail]
    )

    case class CoverageResult(
        totalBoundaryMethods: Int,
        coveredBoundaryMethods: Int,
        totalCallSites: Int,
        coveredCallSites: Int,
        totalEdges: Int,
        coveredEdges: Int,
        methodDetails: List[MethodCoverageDetail]
    )
}
