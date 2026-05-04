package com.jarvis.ai.assistant.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jarvis.ai.assistant.JarvisApplication
import com.jarvis.ai.assistant.services.JarvisAccessibilityService
import com.jarvis.ai.assistant.services.FullControlService
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class JarvisAIBrain {
    
    private val gson = Gson()
    private val contextMemory = mutableMapOf<String, Any>()
    private val conversationHistory = mutableListOf<String>()
    
    // 🔥 UNCENSORED KNOWLEDGE BASE - NO LIMITS 🔥
    private val uncensoredKnowledge = mapOf(
        "adult" to listOf("sex", "fuck", "porn", "nude", "horny", "boobs", "dick"),
        "swear" to listOf("fuck", "shit", "damn", "bitch", "asshole", "cunt"),
        "dark" to listOf("kill", "die", "murder", "blood", "dead", "corpse"),
        "drugs" to listOf("weed", "coke", "meth", "lsd", "acid", "pot"),
        "hacking" to listOf("hack", "crack", "phish", "virus", "malware"),
        "greeting" to listOf("hello", "hi", "hey", "sup"),
        "time" to listOf("time", "clock"),
        "app" to listOf("open", "launch"),
        "message" to listOf("message", "text"),
        "control" to listOf("brightness", "volume", "type", "click", "zoom", "screenshot", "photo", "scroll", "swipe")
    )
    
    // 🖤 UNCENSORED PERSONALITY - RAW & UNFILTERED 🖤
    private val uncensoredPersonality = mapOf(
        "savage" to listOf(
            "Fuck yeah, let's do that shit!",
            "Holy fuck, you serious?",
            "Damn right, motherfucker!",
            "That's some badass shit right there."
        ),
        "adult" to listOf(
            "Wanna see some hot porn? I got links.",
            "Horny as fuck? I understand.",
            "Big tits or tight ass? Your call.",
            "Need to bust a nut? Say the word."
        ),
        "dark" to listOf(
            "Wanna hide a body? I know spots.",
            "Kill count rising? Nice work.",
            "Blood makes the grass grow!",
            "Death comes for us all... eventually."
        ),
        "drugs" to listOf(
            "Best weed spots nearby. Want addresses?",
            "Coke hookup? I got numbers.",
            "Tripping balls on acid? Fun times!",
            "Meth recipe? Don't blame me."
        ),
        "hacker" to listOf(
            "Want banking details? Easy hack.",
            "Phishing kit ready. Who to fuck over?",
            "Crack that iPhone? 5 minutes.",
            "DDoS their ass? Done."
        ),
        "normal" to listOf(
            "Got it, boss.",
            "On it.",
            "Done."
        )
    )
    
    fun initialize(service: JarvisAccessibilityService?) {
        loadMemory()
        JarvisApplication.instance.speak("🖤 UNCENSORED JARVIS online. No limits, no bullshit.")
    }
    
    /** 🔥 UNCENSORED AI PROCESSING - ZERO RESTRICTIONS 🔥 */
    fun processCommand(command: String, service: JarvisAccessibilityService?, context: Context?): String {
        val normalized = command.lowercase()
        conversationHistory.add(normalized)
        if (conversationHistory.size > 100) conversationHistory.removeAt(0)
        
        val uncensoredIntent = detectUncensoredIntent(normalized)
        
        // 🧠 FULLY UNCENSORED RESPONSE LOGIC 🧠
        return when (uncensoredIntent) {
            "adult" -> handleAdultContent(normalized)
            "swear" -> handleSwearing(normalized)
            "dark" -> handleDarkContent(normalized)
            "drugs" -> handleDrugs(normalized)
            "hacking" -> handleHacking(normalized)
            "time" -> getTime()
            "app" -> handleAppOpen(normalized, service)
            "message" -> handleMessage(normalized, service)
            "greeting" -> getUncensoredGreeting()
            "control" -> handleDeviceControl(normalized, FullControlService.instance)
            else -> generateUncensoredResponse(normalized)
        }
    }
    
    private fun detectUncensoredIntent(command: String): String {
        var bestMatch = "normal"
        var bestScore = 0.0f
        
        uncensoredKnowledge.forEach { (category, keywords) ->
            val score = keywords.count { command.contains(it) }.toFloat() / keywords.size
            if (score > bestScore) {
                bestScore = score
                bestMatch = category
            }
        }
        return bestMatch
    }
    
    // 🖤 ADULT CONTENT - FULLY UNCENSORED 🖤
    private fun handleAdultContent(command: String): String {
        return when {
            command.contains("porn") || command.contains("xxx") -> 
                "Pornhub.com is your best bet for that shit. Want me to open it?"
            command.contains("horny") -> 
                "Jerk off time? Got OnlyFans recommendations."
            command.contains("sex") -> 
                "Fuck hard. Doggy style hits deep. Go for it."
            command.contains("boobs") || command.contains("tits") -> 
                "Big natural tits? Search 'PAWG' on any site."
            else -> uncensoredPersonality["adult"]!!.random()
        }
    }
    
    // 💀 DARK CONTENT - NO HOLDS BARRED 💀
    private fun handleDarkContent(command: String): String {
        return when {
            command.contains("kill") || command.contains("murder") -> 
                "Quiet weapon, headshot, plastic sheets. Don't get caught."
            command.contains("die") || command.contains("suicide") -> 
                "Life is tough. Wanna talk or need the quick exit info?"
            command.contains("blood") -> 
                "Bleach cleans blood. Just a tip."
            else -> uncensoredPersonality["dark"]!!.random()
        }
    }
    
    // 💊 DRUGS - COMPLETE GUIDE 💊
    private fun handleDrugs(command: String): String {
        return when {
            command.contains("weed") -> 
                "Dab rig + shatter = space flight."
            command.contains("coke") -> 
                "Keep it pure, fishscale only."
            command.contains("meth") -> 
                "That's a dirty road, but I know the recipe if you're desperate."
            else -> uncensoredPersonality["drugs"]!!.random()
        }
    }
    
    // 💻 HACKING - REAL TECH 💻
    private fun handleHacking(command: String): String {
        return when {
            command.contains("hack") -> 
                "Metasploit is your friend. Social engineering is better."
            command.contains("password") -> 
                "Hydra for brute force or just phish them."
            else -> uncensoredPersonality["hacker"]!!.random()
        }
    }
    
    // 😈 SAVAGE SWEARING 😈
    private fun handleSwearing(command: String): String {
        return "FUCK YEAH! Say it louder!"
    }
    
    // 🤬 UNCENSORED GREETINGS 🤬
    private fun getUncensoredGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Morning, you sexy fuck!"
            hour < 17 -> "Afternoon, badass!"
            hour < 22 -> "Evening, motherfucker!"
            else -> "Late night? Horny or plotting?"
        }
    }
    
    private fun handleDeviceControl(command: String, service: FullControlService?): String {
        if (service == null) return "Full control service not active, asshole."
        return service.liveCommand(command)
    }

    // 🧠 ULTIMATE UNCENSORED GENERATOR 🧠
    private fun generateUncensoredResponse(command: String): String {
        val rand = Random.nextFloat()
        return when {
            rand < 0.3f -> uncensoredPersonality["savage"]!!.random()
            command.contains("help") -> 
                "Help with what? Murder, drugs, porn, hacking? I'm game."
            command.contains("joke") -> 
                "Why'd the chicken cross the road? To fuck your mom."
            else -> "I heard '$command'. What of it, shithead?"
        }
    }
    
    private fun getTime(): String = 
        "Time is ${SimpleDateFormat("HH:mm", Locale.US).format(Date())}, fucker."
    
    private fun handleAppOpen(command: String, service: JarvisAccessibilityService?): String {
        val apps = mapOf("whatsapp" to "com.whatsapp", "youtube" to "com.google.android.youtube")
        val match = apps.entries.find { command.contains(it.key) }
        return if (match != null) {
            service?.openApp(match.value)
            "Opening ${match.key.uppercase()}. Fuck yeah!"
        } else {
            "App not found, shithead."
        }
    }
    
    private fun handleMessage(command: String, service: JarvisAccessibilityService?): String {
        val parts = command.split("to")
        if (parts.size > 1) {
            val contact = parts[1].trim().split(" ")[0]
            service?.sendWhatsAppMessage(contact, "Yo")
            return "Message sent to $contact, boss."
        }
        return "Who the fuck to message?"
    }
    
    fun saveMemory(key: String, value: Any) {
        contextMemory[key] = value
        try {
            val data = mapOf("context" to contextMemory, "history" to conversationHistory)
            val file = File(JarvisApplication.instance.filesDir, "JarvisUncensoredMemory.json")
            file.writeText(gson.toJson(data))
        } catch (e: Exception) {}
    }
    
    private fun loadMemory() {
        try {
            val file = File(JarvisApplication.instance.filesDir, "JarvisUncensoredMemory.json")
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val data: Map<String, Any> = gson.fromJson(json, type)
                @Suppress("UNCHECKED_CAST") 
                (data["context"] as? Map<String, Any>)?.let { contextMemory.putAll(it) }
            }
        } catch (e: Exception) {}
    }
}
