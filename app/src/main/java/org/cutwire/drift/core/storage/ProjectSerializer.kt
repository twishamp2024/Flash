package org.cutwire.drift.core.storage

import org.cutwire.drift.core.model.*
import org.json.JSONArray
import org.json.JSONObject

object ProjectSerializer {

    fun serializeProject(project: Project): String {
        val root = JSONObject()
        root.put("id", project.id)
        root.put("name", project.name)
        root.put("aspectRatio", project.aspectRatio.name)
        root.put("width", project.width)
        root.put("height", project.height)
        root.put("frameRate", project.frameRate)
        root.put("createdAt", project.createdAt)
        root.put("updatedAt", project.updatedAt)
        root.put("schemaVersion", project.schemaVersion)
        project.thumbnailUri?.let { root.put("thumbnailUri", it) }

        // Export settings
        val exportObj = JSONObject()
        exportObj.put("resolutionWidth", project.exportSettings.resolutionWidth)
        exportObj.put("resolutionHeight", project.exportSettings.resolutionHeight)
        exportObj.put("frameRate", project.exportSettings.frameRate)
        exportObj.put("bitRateBps", project.exportSettings.bitRateBps)
        exportObj.put("mimeType", project.exportSettings.mimeType)
        exportObj.put("audioBitRateBps", project.exportSettings.audioBitRateBps)
        exportObj.put("audioSampleRate", project.exportSettings.audioSampleRate)
        root.put("exportSettings", exportObj)

        // Assets
        val assetsArr = JSONArray()
        for (asset in project.assets) {
            val aObj = JSONObject()
            aObj.put("id", asset.id)
            aObj.put("uriString", asset.uriString)
            aObj.put("displayName", asset.displayName)
            aObj.put("mediaType", asset.mediaType.name)
            aObj.put("durationUs", asset.durationUs)
            aObj.put("width", asset.width)
            aObj.put("height", asset.height)
            aObj.put("frameRate", asset.frameRate.toDouble())
            aObj.put("bitRate", asset.bitRate)
            aObj.put("mimeType", asset.mimeType)
            asset.thumbnailPath?.let { aObj.put("thumbnailPath", it) }
            aObj.put("dateAdded", asset.dateAdded)
            assetsArr.put(aObj)
        }
        root.put("assets", assetsArr)

        // Tracks
        val tracksArr = JSONArray()
        for (track in project.tracks) {
            val tObj = JSONObject()
            tObj.put("id", track.id)
            tObj.put("type", track.type.name)
            tObj.put("order", track.order)
            tObj.put("name", track.name)
            tObj.put("isMuted", track.isMuted)
            tObj.put("isHidden", track.isHidden)
            tObj.put("isLocked", track.isLocked)
            tObj.put("volume", track.volume.toDouble())

            val clipsArr = JSONArray()
            for (clip in track.clips) {
                val cObj = JSONObject()
                cObj.put("id", clip.id)
                cObj.put("assetId", clip.assetId)
                cObj.put("timelineStartUs", clip.timelineStartUs)
                cObj.put("sourceStartUs", clip.sourceStartUs)
                cObj.put("sourceEndUs", clip.sourceEndUs)
                cObj.put("durationUs", clip.durationUs)
                cObj.put("playbackSpeed", clip.playbackSpeed.toDouble())
                cObj.put("opacity", clip.opacity.toDouble())
                cObj.put("volume", clip.volume.toDouble())

                // Transform
                val trObj = JSONObject()
                trObj.put("positionX", clip.transform.positionX.toDouble())
                trObj.put("positionY", clip.transform.positionY.toDouble())
                trObj.put("scaleX", clip.transform.scaleX.toDouble())
                trObj.put("scaleY", clip.transform.scaleY.toDouble())
                trObj.put("rotation", clip.transform.rotation.toDouble())
                trObj.put("anchorX", clip.transform.anchorX.toDouble())
                trObj.put("anchorY", clip.transform.anchorY.toDouble())
                cObj.put("transform", trObj)

                // Effects
                val effArr = JSONArray()
                for (eff in clip.effects) {
                    val eObj = JSONObject()
                    eObj.put("id", eff.id)
                    eObj.put("type", eff.type.name)
                    eObj.put("isEnabled", eff.isEnabled)
                    val pObj = JSONObject()
                    for ((k, v) in eff.parameters) {
                        pObj.put(k, v.toDouble())
                    }
                    eObj.put("parameters", pObj)
                    effArr.put(eObj)
                }
                cObj.put("effects", effArr)

                // Transitions
                clip.transitionIn?.let {
                    val tiObj = JSONObject()
                    tiObj.put("id", it.id)
                    tiObj.put("type", it.type.name)
                    tiObj.put("durationUs", it.durationUs)
                    tiObj.put("easing", it.easing.name)
                    cObj.put("transitionIn", tiObj)
                }

                clip.transitionOut?.let {
                    val toObj = JSONObject()
                    toObj.put("id", it.id)
                    toObj.put("type", it.type.name)
                    toObj.put("durationUs", it.durationUs)
                    toObj.put("easing", it.easing.name)
                    cObj.put("transitionOut", toObj)
                }

                // Text Overlay
                clip.textOverlay?.let { to ->
                    val toObj = JSONObject()
                    toObj.put("id", to.id)
                    toObj.put("text", to.text)
                    toObj.put("fontSizeSp", to.fontSizeSp.toDouble())
                    toObj.put("textColor", to.textColor)
                    toObj.put("backgroundColor", to.backgroundColor)
                    toObj.put("strokeColor", to.strokeColor)
                    toObj.put("strokeWidthPx", to.strokeWidthPx.toDouble())
                    toObj.put("alignment", to.alignment.name)
                    toObj.put("animation", to.animation.name)
                    cObj.put("textOverlay", toObj)
                }

                clipsArr.put(cObj)
            }
            tObj.put("clips", clipsArr)
            tracksArr.put(tObj)
        }
        root.put("tracks", tracksArr)

        return root.toString()
    }

    fun deserializeProject(jsonString: String): Project {
        val root = JSONObject(jsonString)
        val id = root.optString("id", "")
        val name = root.optString("name", "Untitled")
        val aspectRatioName = root.optString("aspectRatio", AspectRatio.RATIO_16_9.name)
        val aspectRatio = try {
            AspectRatio.valueOf(aspectRatioName)
        } catch (_: Exception) {
            AspectRatio.RATIO_16_9
        }
        val width = root.optInt("width", aspectRatio.defaultWidth)
        val height = root.optInt("height", aspectRatio.defaultHeight)
        val frameRate = root.optInt("frameRate", 30)
        val createdAt = root.optLong("createdAt", System.currentTimeMillis())
        val updatedAt = root.optLong("updatedAt", System.currentTimeMillis())
        val schemaVersion = root.optInt("schemaVersion", 1)
        val thumbnailUri = if (root.has("thumbnailUri")) root.getString("thumbnailUri") else null

        // Export settings
        val exportSettings = if (root.has("exportSettings")) {
            val e = root.getJSONObject("exportSettings")
            ExportSettings(
                resolutionWidth = e.optInt("resolutionWidth", width),
                resolutionHeight = e.optInt("resolutionHeight", height),
                frameRate = e.optInt("frameRate", frameRate),
                bitRateBps = e.optLong("bitRateBps", 10_000_000L),
                mimeType = e.optString("mimeType", "video/avc"),
                audioBitRateBps = e.optInt("audioBitRateBps", 192_000),
                audioSampleRate = e.optInt("audioSampleRate", 44_100)
            )
        } else {
            ExportSettings(resolutionWidth = width, resolutionHeight = height, frameRate = frameRate)
        }

        // Assets
        val assets = mutableListOf<MediaAsset>()
        if (root.has("assets")) {
            val aArr = root.getJSONArray("assets")
            for (i in 0 until aArr.length()) {
                val a = aArr.getJSONObject(i)
                val mTypeStr = a.optString("mediaType", MediaType.VIDEO.name)
                val mType = try { MediaType.valueOf(mTypeStr) } catch (_: Exception) { MediaType.VIDEO }
                assets.add(
                    MediaAsset(
                        id = a.optString("id"),
                        uriString = a.optString("uriString"),
                        displayName = a.optString("displayName"),
                        mediaType = mType,
                        durationUs = a.optLong("durationUs"),
                        width = a.optInt("width"),
                        height = a.optInt("height"),
                        frameRate = a.optDouble("frameRate", 30.0).toFloat(),
                        bitRate = a.optLong("bitRate"),
                        mimeType = a.optString("mimeType"),
                        thumbnailPath = if (a.has("thumbnailPath")) a.getString("thumbnailPath") else null,
                        dateAdded = a.optLong("dateAdded", System.currentTimeMillis())
                    )
                )
            }
        }

        // Tracks
        val tracks = mutableListOf<Track>()
        if (root.has("tracks")) {
            val tArr = root.getJSONArray("tracks")
            for (i in 0 until tArr.length()) {
                val t = tArr.getJSONObject(i)
                val tTypeStr = t.optString("type", TrackType.VIDEO.name)
                val tType = try { TrackType.valueOf(tTypeStr) } catch (_: Exception) { TrackType.VIDEO }

                val clips = mutableListOf<Clip>()
                if (t.has("clips")) {
                    val cArr = t.getJSONArray("clips")
                    for (j in 0 until cArr.length()) {
                        val c = cArr.getJSONObject(j)
                        val transform = if (c.has("transform")) {
                            val tr = c.getJSONObject("transform")
                            ClipTransform(
                                positionX = tr.optDouble("positionX", 0.0).toFloat(),
                                positionY = tr.optDouble("positionY", 0.0).toFloat(),
                                scaleX = tr.optDouble("scaleX", 1.0).toFloat(),
                                scaleY = tr.optDouble("scaleY", 1.0).toFloat(),
                                rotation = tr.optDouble("rotation", 0.0).toFloat(),
                                anchorX = tr.optDouble("anchorX", 0.5).toFloat(),
                                anchorY = tr.optDouble("anchorY", 0.5).toFloat()
                            )
                        } else {
                            ClipTransform()
                        }

                        val effects = mutableListOf<EffectInstance>()
                        if (c.has("effects")) {
                            val eArr = c.getJSONArray("effects")
                            for (k in 0 until eArr.length()) {
                                val e = eArr.getJSONObject(k)
                                val eTypeStr = e.optString("type", EffectType.BRIGHTNESS.name)
                                val eType = try { EffectType.valueOf(eTypeStr) } catch (_: Exception) { EffectType.BRIGHTNESS }
                                val pMap = mutableMapOf<String, Float>()
                                if (e.has("parameters")) {
                                    val pObj = e.getJSONObject("parameters")
                                    val keys = pObj.keys()
                                    while (keys.hasNext()) {
                                        val key = keys.next()
                                        pMap[key] = pObj.optDouble(key, 0.0).toFloat()
                                    }
                                }
                                effects.add(
                                    EffectInstance(
                                        id = e.optString("id"),
                                        type = eType,
                                        isEnabled = e.optBoolean("isEnabled", true),
                                        parameters = pMap
                                    )
                                )
                            }
                        }

                        val ti = if (c.has("transitionIn")) {
                            val tio = c.getJSONObject("transitionIn")
                            val tTypeIn = try { TransitionType.valueOf(tio.optString("type")) } catch (_: Exception) { TransitionType.CROSSFADE }
                            val easing = try { EasingCurve.valueOf(tio.optString("easing")) } catch (_: Exception) { EasingCurve.EASE_IN_OUT }
                            TransitionInstance(
                                id = tio.optString("id"),
                                type = tTypeIn,
                                durationUs = tio.optLong("durationUs", 500_000L),
                                easing = easing
                            )
                        } else null

                        val to = if (c.has("transitionOut")) {
                            val too = c.getJSONObject("transitionOut")
                            val tTypeOut = try { TransitionType.valueOf(too.optString("type")) } catch (_: Exception) { TransitionType.CROSSFADE }
                            val easing = try { EasingCurve.valueOf(too.optString("easing")) } catch (_: Exception) { EasingCurve.EASE_IN_OUT }
                            TransitionInstance(
                                id = too.optString("id"),
                                type = tTypeOut,
                                durationUs = too.optLong("durationUs", 500_000L),
                                easing = easing
                            )
                        } else null

                        val textOverlay = if (c.has("textOverlay")) {
                            val too = c.getJSONObject("textOverlay")
                            val align = try { TextAlignment.valueOf(too.optString("alignment")) } catch (_: Exception) { TextAlignment.CENTER }
                            val anim = try { TextAnimation.valueOf(too.optString("animation")) } catch (_: Exception) { TextAnimation.NONE }
                            TextOverlay(
                                id = too.optString("id"),
                                text = too.optString("text"),
                                fontSizeSp = too.optDouble("fontSizeSp", 28.0).toFloat(),
                                textColor = too.optLong("textColor", 0xFFFFFFFFL),
                                backgroundColor = too.optLong("backgroundColor", 0x00000000L),
                                strokeColor = too.optLong("strokeColor", 0xFF000000L),
                                strokeWidthPx = too.optDouble("strokeWidthPx", 2.0).toFloat(),
                                alignment = align,
                                animation = anim
                            )
                        } else null

                        clips.add(
                            Clip(
                                id = c.optString("id"),
                                assetId = c.optString("assetId"),
                                timelineStartUs = c.optLong("timelineStartUs"),
                                sourceStartUs = c.optLong("sourceStartUs"),
                                sourceEndUs = c.optLong("sourceEndUs"),
                                durationUs = c.optLong("durationUs"),
                                playbackSpeed = c.optDouble("playbackSpeed", 1.0).toFloat(),
                                transform = transform,
                                opacity = c.optDouble("opacity", 1.0).toFloat(),
                                volume = c.optDouble("volume", 1.0).toFloat(),
                                effects = effects,
                                transitionIn = ti,
                                transitionOut = to,
                                textOverlay = textOverlay
                            )
                        )
                    }
                }

                tracks.add(
                    Track(
                        id = t.optString("id"),
                        type = tType,
                        order = t.optInt("order", i),
                        name = t.optString("name", "Track ${i + 1}"),
                        isMuted = t.optBoolean("isMuted", false),
                        isHidden = t.optBoolean("isHidden", false),
                        isLocked = t.optBoolean("isLocked", false),
                        volume = t.optDouble("volume", 1.0).toFloat(),
                        clips = clips
                    )
                )
            }
        }

        return Project(
            id = id,
            name = name,
            aspectRatio = aspectRatio,
            width = width,
            height = height,
            frameRate = frameRate,
            tracks = tracks,
            assets = assets,
            exportSettings = exportSettings,
            thumbnailUri = thumbnailUri,
            createdAt = createdAt,
            updatedAt = updatedAt,
            schemaVersion = schemaVersion
        )
    }
}
