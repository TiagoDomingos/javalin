/*
 * Javalin - https://javalin.io
 * Copyright 2017 David Åse
 * Licensed under Apache 2.0: https://github.com/tipsy/javalin/blob/master/LICENSE
 */

@file:JvmName("RouteOverviewUtil")

package io.javalin.core.util

import io.javalin.Handler
import io.javalin.Javalin
import io.javalin.core.HandlerType
import io.javalin.security.Role
import sun.reflect.ConstantPool

data class RouteOverviewEntry(val httpMethod: HandlerType, val path: String, val handler: Handler, val roles: List<Role>?)

fun createHtmlOverview(app: Javalin): String {
    return """
        <meta name='viewport' content='width=device-width, initial-scale=1'>
        <style>
            * {
                box-sizing: border-box;
            }
            b, thead {
                font-weight: 700;
            }
            html {
                background: #363e4c;
            }
            body {
                font-family: monospace;
                padding: 25px;
            }
            table {
                background: #fff;
                border-spacing: 0;
                font-size: 14px;
                width: 100%;
                white-space: pre;
                box-shadow: 0 5px 25px rgba(0,0,0,0.25);
            }
            thead {
                background: #1a202b;
                color: #fff;
            }
            thead td {
                border-bottom: 2px solid #000;
            }
            tr + tr td {
                border-top: 1px solid rgba(0, 0, 0, 0.25);
            }
            tr + tr td:first-of-type {
                border-top: 1px solid rgba(0, 0, 0, 0.35);
            }
            td {
                padding: 10px 15px;
            }
            tbody td:not(:first-of-type) {
                background-color: rgba(255,255,255,0.925);
            }
            tbody tr:hover td:not(:first-of-type) {
                background-color: rgba(255,255,255,0.85);
            }
            .method td:first-of-type {
                text-align: center;
                max-width: 80px;
            }
            tbody .method td:first-of-type {
                color: #fff;
                text-shadow: 1px 1px 0px rgba(0,0,0,0.15);
                border-left: 6px solid rgba(0, 0, 0, 0.35);
                border-right: 1px solid rgba(0, 0, 0, 0.15);
            }
            .GET {
                background: #5a76ff;
            }
            .POST {
                background: #5dca5d;
            }
            .PUT, .PATCH {
                background: #ef9a00;
            }
            .DELETE {
                background: #ef4848;
            }
            .HEAD, .TRACE, .OPTIONS  {
                background: #00b9b9;
            }
        </style>
        <body>
            <table>
                <thead>
                    <tr class="method">
                        <td width="90px">Method</td>
                        <td>Path</td>
                        <td>Handler</td>
                        <td>Roles</td>
                    </tr>
                </thead>
                ${app.routeOverviewEntries.map { (httpMethod, path, handler, roles) ->
        """
                    <tr class="method $httpMethod">
                        <td>$httpMethod</span></td>
                        <td>$path</td>
                        <td><b>${handler.metaInfo}</b></td>
                        <td>${roles?.toString() ?: "-"}</td>
                    </tr>
                    """
    }.joinToString("")}
            </table>
        </body>
    """
}

private const val lambdaSign = "??? (anonymous lambda)"

private val Handler.parentClass: Class<*> get() = Class.forName(this.javaClass.name.takeWhile { it != '$' })
private val Handler.implementingClassName: String? get() = this.javaClass.name

private val Handler.isKotlinAnonymousLambda: Boolean get() = this.javaClass.enclosingMethod != null
private val Handler.isKotlinMethodReference: Boolean get() = this.javaClass.declaredFields.any { it.name == "function" }
private val Handler.isKotlinField: Boolean get() = this.javaClass.fields.any { it.name == "INSTANCE" }

private val Handler.isJavaAnonymousLambda: Boolean get() = this.javaClass.isSynthetic
private val Handler.isJavaMethodReference: Boolean get() = this.methodName != null
private val Handler.isJavaField: Boolean get() = this.fieldName != null

private fun Any.runMethod(name: String): Any = this.javaClass.getMethod(name).apply { isAccessible = true }.invoke(this)

val Handler.metaInfo: String
    get() {
        // this is just guesswork...
        return when {
            isKotlinMethodReference -> {
                val f = this.javaClass.getDeclaredField("function")
                        .apply { isAccessible = true }
                        .get(this)
                f.runMethod("getOwner").runMethod("getJClass").runMethod("getName").toString() + "::" + f.runMethod("getName")
            }
            isKotlinAnonymousLambda -> parentClass.name + "::" + lambdaSign
            isKotlinField -> parentClass.name + "." + fieldName

            isJavaMethodReference -> parentClass.name + "::" + methodName
            isJavaField -> parentClass.name + "." + fieldName
            isJavaAnonymousLambda -> parentClass.name + "::" + lambdaSign

            else -> implementingClassName + ".class"
        }
    }

val Handler.fieldName: String?
    get() = try {
        parentClass.declaredFields.find { it.isAccessible = true; it.get(it) == this }?.name
    } catch (ignored: Exception) { // Nothing really matters.
        null
    }

val Handler.methodName: String?
    get() {
        val constantPool = Class::class.java.getDeclaredMethod("getConstantPool").apply { isAccessible = true }.invoke(javaClass) as ConstantPool
        for (i in constantPool.size downTo 0) {
            try {
                val name = constantPool.getMemberRefInfoAt(i)[1];
                // Autogenerated ($), constructor, or kotlin's check (fix maybe?)
                if (name.contains("(\\$|<init>|checkParameterIsNotNull)".toRegex())) {
                    continue
                } else {
                    return name
                }
            } catch (ignored: Exception) {
            }
        }
        return null
    }
