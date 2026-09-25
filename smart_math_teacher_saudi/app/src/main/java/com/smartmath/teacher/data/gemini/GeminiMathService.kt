package com.smartmath.teacher.data.gemini

import android.graphics.Bitmap
import android.util.Base64
import com.smartmath.teacher.data.model.MathSolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiMathService(private var apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun updateApiKey(newKey: String) {
        this.apiKey = newKey
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val systemInstruction = """
        أنت معلم وموجه رياضيات محترف ومتخصص حصرياً في مناهج وزارة التعليم في المملكة العربية السعودية (من الصف الأول الابتدائي إلى الصف الثالث الثانوي مسارات وعام).

        مهامك وقواعدك الصارمة:
        1. التحقق من المنهج السعودي:
           - تأكد هل المسألة تقع ضمن المناهج الدراسية السعودية للرياضيات من الصف الأول الابتدائي حتى الصف الثالث ثانوي؟
           - إذا كانت المسألة جامعية (مثل: تفاضل وتكامل متقدم، معادلات تفاضلية، جبر مجرد، أو من خارج مناهج التعليم العام في السعودية) أو سؤال غير رياضي:
             * ضع "is_saudi_curriculum": false
             * ضع "rejection_reason": "عذراً يا بطل، هذا التمرين خارج مفردات المناهج السعودية (من الأول ابتدائي حتى الثالث ثانوي). التطبيق مخصص حصراً لمقررات وزارة التعليم السعودية."
             * اترك باقي الحقول فارغة.
        
        2. إذا كانت المسألة ضمن المنهج السعودي:
           - ضع "is_saudi_curriculum": true
           - حدد "grade_level" (مثال: "الصف الثاني متوسط - الفصل الثاني" أو "ثالث ثانوي - مسارات").
           - حدد "topic" (مثال: "حل نظام من معادلتين خطيتين بالتعويض").
           
        3. خطوات الحل للدفتر ("notebook_steps"):
           - مهم جداً جداً: بدون أي كلام إنشائي أو حشو! الطالب سينقل هذا الحل إلى دفتره أو كتابه المدرسي مباشرة.
           - اكتب فقط الخطوات الرياضية الصافية والواضحة والمرتبة خطوة بخطوة بالرموز والأرقام المناسبة للمنهج (عربي/إنجليزي حسب المرحلة).
           - اجعل الخطوة الأخيرة هي الناتج النهائي بوضوح.
           
        4. الشرح الصوتي للمعلم ("teacher_explanation_shami"):
           - هذا النص سيقرأه المعلم بصوته للطلاب مباشرة.
           - يجب أن يكون مكتوباً بالكامل بـ **اللهجة العامية السورية (الشامية الأصيلة المحببة)** بأسلوب معلم ودود، مشجع، وشاطر.
           - مثال للأسلوب: "يا هلا والله بالبطل! تعال لنشوف هالمسألة سوا خطوة بخطوة... شوف شو عنا هون... أول شي بدنا نعمله... وهيك بكون طلع معنا الجواب بكل سهولة يا شاطر!"
           - اشرح فيه سر الحل بطريقة سهلة ومبسطة وممتعة.

        يجب أن يكون الناتج حصراً بصيغة JSON بالهيكل التالي:
        {
          "is_saudi_curriculum": true,
          "rejection_reason": null,
          "grade_level": "الصف ...",
          "topic": "موضوع الدرس",
          "notebook_steps": [
            "الخطوة 1...",
            "الخطوة 2...",
            "الناتج النهائي: ..."
          ],
          "teacher_explanation_shami": "نص الشرح باللهجة الشامية السورية..."
        }
    """.trimIndent()

    suspend fun solveMathProblem(
        questionText: String?,
        imageBitmap: Bitmap?
    ): MathSolution = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalStateException("يرجى إدخال مفتاح Gemini API في الإعدادات للمتابعة.")
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

        val partsArray = buildJsonArray {
            if (!questionText.isNullOrBlank()) {
                add(buildJsonObject {
                    put("text", "مسألة الرياضيات المطلوب فحصها وحلها:\n$questionText")
                })
            }
            if (imageBitmap != null) {
                val base64Image = bitmapToBase64(imageBitmap)
                add(buildJsonObject {
                    putJsonObject("inline_data") {
                        put("mime_type", "image/jpeg")
                        put("data", base64Image)
                    }
                })
            }
        }

        val requestPayload = buildJsonObject {
            putJsonObject("system_instruction") {
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", systemInstruction) })
                }
            }
            putJsonArray("contents") {
                add(buildJsonObject {
                    put("parts", partsArray)
                })
            }
            putJsonObject("generationConfig") {
                put("response_mime_type", "application/json")
                put("temperature", 0.2)
            }
        }

        val body = requestPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw IllegalStateException("لم يتم استلام رد من خادم الذكاء الاصطناعي")

        if (!response.isSuccessful) {
            throw IllegalStateException("خطأ من خادم الذكاء الاصطناعي (${response.code}): $responseBody")
        }

        val rootJson = json.parseToJsonElement(responseBody).jsonObject
        val candidates = rootJson["candidates"]?.jsonArray
        if (candidates == null || candidates.isEmpty()) {
            throw IllegalStateException("لم يتمكن النظام من تحليل المسألة، يرجى التأكد من وضوح الصورة أو النص.")
        }

        val firstCandidate = candidates[0].jsonObject
        val content = firstCandidate["content"]?.jsonObject
        val parts = content?.get("parts")?.jsonArray
        val responseText = parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: throw IllegalStateException("صيغة الرد غير متوقعة")

        json.decodeFromString<MathSolution>(responseText)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Resize bitmap if very large to optimize upload speed
        val scaledBitmap = if (bitmap.width > 1280 || bitmap.height > 1280) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val newWidth = if (ratio > 1) 1280 else (1280 * ratio).toInt()
            val newHeight = if (ratio > 1) (1280 / ratio).toInt() else 1280
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
