package doyoung.practice.stt

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import doyoung.practice.stt.databinding.ActivityMainBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread
import kotlin.concurrent.timer

private lateinit var intent: Intent
private lateinit var speechRecognizer: SpeechRecognizer


// 녹음 중인지에 대한 여부
private var recording = false
private lateinit var contents: EditText
private lateinit var contentsResult: String
private lateinit var msgToServer: String
private lateinit var departure: String
private lateinit var destination: String
private lateinit var busNumber: String
//busNumber를 통해 Open API 연동만 하면 앱 역할은 끝

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val REQUEST_RECORD_AUDIO_CODE = 200
    }

    var tts: TextToSpeech? = null
    private lateinit var testTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermission() //녹음에 대한 권한 확인

        // R 파일로 UI 설정
        val btnRecord = findViewById<Button>(R.id.btnRecord)
        val btnSignal = findViewById<Button>(R.id.btnSignal)
        contents = findViewById(R.id.editTextContent)
        testTextView = findViewById(R.id.testTextView)

        tts = TextToSpeech(applicationContext) { status ->
            if (status != TextToSpeech.ERROR) {
                tts!!.language = Locale.KOREAN
            }
        }

        btnSignal.setOnClickListener {
            // 서버에 승차 완료에 대한 메시지 보냄 ex) "탑승 완료한 사용자는 신창역에서 내릴 것이니 유의 바랍니다."
            val msgToServer2 = departure
            sendBoardingCompleteSignal(msgToServer2)
        }

        btnSignal.setOnLongClickListener {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP)

            sendExitMessage()

            true // 이벤트를 소비한 경우 true를 반환합니다.
        }

        // 녹음 버튼 클릭 리스너에 대한 설정
        btnRecord.setOnClickListener {
            if (!recording) {
                StartRecord() // recording = true
                Toast.makeText(applicationContext, "지금부터 음성 녹음을 시작합니다.", Toast.LENGTH_SHORT).show()
            } else {
                StopRecord()
            }
        }

        fun onPause() {
            tts?.stop()
            tts?.shutdown()
            super.onPause()
        }

        // RecognizerIntent 객체 생성
        intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName())
        // 한국어로 입력 받음
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
    }


    // 녹음 시작
    private fun StartRecord() {
        contents.setText("")
        recording = true

        binding.btnRecord.setText("음성 녹음 중지") // applicationContext 부분의 오류 가능성
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
        speechRecognizer.setRecognitionListener(listener)
        speechRecognizer.startListening(intent)
    }

    // 녹음 종료
    private fun StopRecord() {
        recording = false
        binding.btnRecord.setText("음성 녹음 시작")
        speechRecognizer.stopListening()
        Toast.makeText(applicationContext, "음성 기록 중지합니다.", Toast.LENGTH_SHORT).show()

    }

    // SpeechRecognizer의 정의 (리스너의 생성)
    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {

        }

        override fun onBeginningOfSpeech() {
            // 사용자가 말하기 시작함.
        }

        override fun onRmsChanged(rmsdB: Float) {

        }

        override fun onBufferReceived(buffer: ByteArray?) {

        }

        override fun onEndOfSpeech() {
            // 사용자가 말을 멈추면 호출된다.
            // 인식 결과에 따라 onError나 onResult가 호출된다.
        }

        override fun onError(error: Int) { //토스트 에러 메시지 출력
            val message: String = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "오디오 에러"
                SpeechRecognizer.ERROR_CLIENT -> return //speechRecognizer.stopListening()을 호출하면 발생하는 에러
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "퍼미션 없음"
                SpeechRecognizer.ERROR_NETWORK -> "네트워크 에러"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트웍 타임아웃"
                SpeechRecognizer.ERROR_NO_MATCH -> return //녹음을 오래하거나 speechRecognizer.stopListening()을 호출하면 발생하는 에러
                //speechRecognizer를 다시 생성하여 녹음 재개
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RECOGNIZER가 바쁨"
                SpeechRecognizer.ERROR_SERVER -> "서버가 이상함"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "말하는 시간초과"
                else -> "알 수 없는 오류임"
            }
            Toast.makeText(applicationContext, "에러가 발생하였습니다. : $message", Toast.LENGTH_SHORT).show()
        }

        // 인식 결과 준비 완료 시 호출
        override fun onResults(results: Bundle?) {
            val matches: ArrayList<String>? =
                results!!.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val originText: String = contents.text.toString()

            var newText = ""
            matches?.forEach { newText += it }

            contents.setText("입력하신 내용  " + originText + newText + "  으로 서비스를 진행하겠습니다.     정보가 잘못되었다면 다시 버튼을 눌러주세요")


            // TTS_ 읽어주기
            val toSpeak = contents.text.toString()
            tts!!.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null)

            // 서버로 보낼 메시지 파싱하기
            contentsResult = "$originText"+ "$newText"
            // 출발지, 목적지, 버스 번호는 각각 departure, destination, busNumber의 변수명을 가진 전역 변수
            assignValuesFromVoiceInput(contentsResult)

            // 파싱한 데이터를 바탕으로 서버에 승차 희망 메시지 설정
            msgToServer = "$departure -> $destination"

            // TTS 서비스를 한 후 3초간 대기했다가 msgToServer 메시지 값을 서버로 보낸다.
            val handler = Handler()

            // 일정 시간(예: 3초) 후에 서버에 메시지 보내기
            handler.postDelayed({
                SendMessage(msgToServer)
            }, 3000)

        }

        override fun onPartialResults(partialResults: Bundle?) {

        }

        override fun onEvent(eventType: Int, params: Bundle?) {

        }

        override fun onSegmentResults(segmentResults: Bundle) {
            super.onSegmentResults(segmentResults)
        }

        override fun onEndOfSegmentedSession() {
            super.onEndOfSegmentedSession()
        }
    }

    // 음성 메시지 파싱
    fun assignValuesFromVoiceInput(input: String) {
        val parsedValues = parseVoiceInput(input)
        departure = parsedValues.first ?: "출발지"
        destination = parsedValues.second ?: "목적지"
        busNumber = parsedValues.third ?: "00"
    }


    fun parseVoiceInput(input: String): Triple<String?, String?, String?> {
        var input = input // 변수를 var로 변경
        val keywords = listOf("에서", "까지", "번 버스")
        val values = mutableListOf<String?>()

        for (keyword in keywords) {
            val startIndex = input.indexOf(keyword)
            if (startIndex != -1) {
                val endIndex = startIndex + keyword.length
                val value = input.substring(0, startIndex).trim()
                input = input.substring(endIndex).trim()
                values.add(value)
            } else {
                values.add(null)
            }
        }

        return Triple(values[0], values[1], values[2])
    }

    // STT 기능을 활용해 전달받은 메시지를 서버에 보냄 ex) message = 신창마트 -> 후문
    fun SendMessage(message: String) {
        val client = OkHttpClient()

        val mediaType = "application/vnd.onem2m-res+json; ty=4".toMediaType()
        val body = "{\n    \"m2m:cin\": {\n        \"con\": \"$message\"\n    }\n}".toRequestBody(mediaType)
        val request = Request.Builder()
            .url("http://203.253.128.177:7579/Mobius/Yoon/PathFromUser")
            .post(body)
            .addHeader("Accept", "application/json")
            .addHeader("X-M2M-RI", "12345")
            .addHeader("X-M2M-Origin", "Sbhr3Xo7Vz_C")
            .addHeader("Content-Type", "application/vnd.onem2m-res+json; ty=4")
            .build()




        // 요청에 대한 콜백
        val callback = object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Client", e.toString())
            }

            override fun onResponse(call: Call, response: Response) {
                if(response.isSuccessful) {
                    Log.e("Client", "${response.body?.string()}")
                }
            }
        }

        // 요청 보내기
        client.newCall(request).enqueue(callback)
    }

    // 탑승 완료 신호 보내기 ex) 탑승 완료한 사용자는 신창역에서 내릴 것이니 유의 바랍니다. _ RPi에 지속적 디스플레이
    fun sendBoardingCompleteSignal(msgToServer2: String) {
        val client = OkHttpClient()
        val message = "탑승 완료한 사용자는 $msgToServer2 에서 내릴 것이니 유의 바랍니다."
        val mediaType = "application/vnd.onem2m-res+json; ty=4".toMediaType()
        val body = "{\n    \"m2m:cin\": {\n        \"con\": \"$message\"\n    }\n}".toRequestBody(mediaType)
        val request = Request.Builder()
            .url("http://203.253.128.177:7579/Mobius/Yoon/PathFromUser")
            .post(body)
            .addHeader("Accept", "application/json")
            .addHeader("X-M2M-RI", "12345")
            .addHeader("X-M2M-Origin", "Sbhr3Xo7Vz_C")
            .addHeader("Content-Type", "application/vnd.onem2m-res+json; ty=4")
            .build()

        // 요청에 대한 콜백
        val callback = object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Client", e.toString())
            }

            override fun onResponse(call: Call, response: Response) {
                if(response.isSuccessful) {
                    Log.e("Client", "${response.body?.string()}")
                }
            }
        }

        // 요청 보내기
        client.newCall(request).enqueue(callback)
    }

    // 하차 완료 신호 보내기 ex) "exit". _ RPi의 디스플레이를 해제
    fun sendExitMessage() {
        val client = OkHttpClient()
        val message = "exit"
        val mediaType = "application/vnd.onem2m-res+json; ty=4".toMediaType()
        val body = "{\n    \"m2m:cin\": {\n        \"con\": \"$message\"\n    }\n}".toRequestBody(mediaType)
        val request = Request.Builder()
            .url("http://203.253.128.177:7579/Mobius/Yoon/PathFromUser")
            .post(body)
            .addHeader("Accept", "application/json")
            .addHeader("X-M2M-RI", "12345")
            .addHeader("X-M2M-Origin", "Sbhr3Xo7Vz_C")
            .addHeader("Content-Type", "application/vnd.onem2m-res+json; ty=4")
            .build()

        // 요청에 대한 콜백
        val callback = object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Client", e.toString())
            }

            override fun onResponse(call: Call, response: Response) {
                if(response.isSuccessful) {
                    Log.e("Client", "${response.body?.string()}")
                }
            }
        }

        // 요청 보내기
        client.newCall(request).enqueue(callback)
    }

    // 하위 코드 모두 권한 관련 코드
    // 권한 확인 (인터넷, 녹음)
    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            // 권한 요청
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.INTERNET
                ) == PackageManager.PERMISSION_DENIED
                || ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_DENIED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        android.Manifest.permission.RECORD_AUDIO,
                        android.Manifest.permission.INTERNET
                    ),
                    REQUEST_RECORD_AUDIO_CODE
                )
            }
        }
    }

    private fun showPermissionRationalDialog() {
        AlertDialog.Builder(this)
            .setMessage("녹음 권한을 켜주셔야지 앱을 정상적으로 사용할 수 있습니다.")
            .setPositiveButton("권한 허용하기") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.RECORD_AUDIO),
                    REQUEST_RECORD_AUDIO_CODE
                )
            }.setNegativeButton("취소") { dialogInterface, _ -> dialogInterface.cancel() }
            .show()
    }

    private fun showPermissionSettingDialog() {
        AlertDialog.Builder(this)
            .setMessage("녹음 권한을 켜주셔야지 앱을 정상적으로 사용할 수 있습니다. 앱 설정 화면으로 진입하셔서 권한을 켜주세요.")
            .setPositiveButton("권한 변경하러 가기") { _, _ ->
                navigateToAppSetting()
            }.setNegativeButton("취소") { dialogInterface, _ -> dialogInterface.cancel() }
            .show()
    }

    private fun navigateToAppSetting() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null) // packageName에 해당하는 디테일 세팅으로 가겠다.
        }
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val audioRecordPermissionGranted = requestCode == REQUEST_RECORD_AUDIO_CODE
                && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED

        if (audioRecordPermissionGranted) {

        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                )
            ) {
                showPermissionRationalDialog()
            } else {
                showPermissionSettingDialog()
            }
        }
    }
}
