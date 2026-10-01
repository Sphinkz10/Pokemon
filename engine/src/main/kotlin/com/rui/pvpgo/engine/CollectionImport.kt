package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object CollectionImportService {
    private val supported = setOf(
        "externalid", "speciesid", "species", "ivattack", "attack", "ivdefense", "defense", "ivstamina", "stamina",
        "cp", "level", "nickname", "isshadow", "shadow", "ispurified", "purified", "isshiny", "shiny",
        "islucky", "lucky", "isbestbuddy", "bestbuddy", "isfavorite", "favorite", "fastmoveid", "fastmove",
        "chargedmoveids", "chargedmoves", "secondchargedmoveunlocked", "tags", "caughtatepochms"
    )

    fun parseCsv(text: String, sourceLabel: String, importedAtEpochMs: Long): CollectionImportBatch {
        require(sourceLabel.isNotBlank())
        require(importedAtEpochMs >= 0)
        val rows = parseCsvRows(text)
        if (rows.isEmpty()) return CollectionImportBatch(CollectionImportFormat.CSV, sourceLabel, importedAtEpochMs, emptyList(), emptyList(), 0)
        val headers = rows.first().map(::norm)
        val issues = mutableListOf<CollectionImportIssue>()
        headers.forEachIndexed { i, h ->
            if (h !in supported) issues += CollectionImportIssue(1, CollectionImportIssueCode.UNSUPPORTED_FIELD, rows.first()[i], "Campo ignorado: ${rows.first()[i]}")
        }
        val maps = rows.drop(1).filter { it.any(String::isNotBlank) }.mapIndexed { idx, values ->
            val map = linkedMapOf<String, Any?>()
            headers.forEachIndexed { col, header -> map[header] = values.getOrNull(col)?.trim().orEmpty() }
            (idx + 2) to map
        }
        return buildBatch(CollectionImportFormat.CSV, sourceLabel, importedAtEpochMs, maps, issues)
    }

    fun parseManual(fields: Map<String, Any?>, sourceLabel: String, importedAtEpochMs: Long): CollectionImportBatch {
        require(sourceLabel.isNotBlank())
        require(importedAtEpochMs >= 0)
        val normalized = linkedMapOf<String, Any?>()
        val issues = mutableListOf<CollectionImportIssue>()
        fields.forEach { (k, v) ->
            val key = norm(k)
            if (key in supported) normalized[key] = v
            else issues += CollectionImportIssue(1, CollectionImportIssueCode.UNSUPPORTED_FIELD, k, "Campo ignorado: $k")
        }
        return buildBatch(CollectionImportFormat.MANUAL, sourceLabel, importedAtEpochMs, listOf(1 to normalized), issues)
    }

    fun parseJson(text: String, sourceLabel: String, importedAtEpochMs: Long): CollectionImportBatch {
        require(sourceLabel.isNotBlank())
        require(importedAtEpochMs >= 0)
        val parsed = JsonMiniParser(text).parse()
        val objects = when (parsed) {
            is List<*> -> parsed
            is Map<*, *> -> (parsed["pokemon"] ?: parsed["collection"] ?: parsed["items"]) as? List<*>
                ?: error("JSON must be an array or contain pokemon/collection/items array")
            else -> error("JSON must contain an array of objects")
        }
        val rows = mutableListOf<Pair<Int, Map<String, Any?>>>()
        val issues = mutableListOf<CollectionImportIssue>()
        objects.forEachIndexed { index, value ->
            val row = index + 1
            val obj = value as? Map<*, *>
            if (obj == null) {
                issues += CollectionImportIssue(row, CollectionImportIssueCode.INVALID_ROW, null, "JSON row is not an object")
            } else {
                val normalized = linkedMapOf<String, Any?>()
                obj.forEach { (k, v) ->
                    val key = norm(k?.toString().orEmpty())
                    if (key in supported) normalized[key] = v
                    else issues += CollectionImportIssue(row, CollectionImportIssueCode.UNSUPPORTED_FIELD, k?.toString(), "Campo ignorado: $k")
                }
                rows += row to normalized
            }
        }
        return buildBatch(CollectionImportFormat.JSON, sourceLabel, importedAtEpochMs, rows, issues)
    }

    fun reconcile(batch: CollectionImportBatch, collection: List<OwnedPokemon>): ReconciledImportBatch =
        ReconciledImportBatch(
            batch,
            batch.candidates.map { candidate ->
                ReconciledImportCandidate(candidate, CollectionReconciler.reconcile(candidate.candidate, collection))
            }
        )

    fun toInboxItems(batch: ReconciledImportBatch): List<CandidateInboxItem> = batch.reconciled.map { row ->
        CandidateInboxItem(
            id = "inbox-${row.importCandidate.candidate.id}",
            candidate = row.importCandidate.candidate,
            createdAtEpochMs = batch.importBatch.importedAtEpochMs,
            status = CandidateInboxStatus.PENDING
        )
    }

    private fun buildBatch(
        format: CollectionImportFormat,
        sourceLabel: String,
        importedAtEpochMs: Long,
        rows: List<Pair<Int, Map<String, Any?>>>,
        initialIssues: MutableList<CollectionImportIssue>
    ): CollectionImportBatch {
        val issues = initialIssues.toMutableList()
        val candidates = mutableListOf<CollectionImportCandidate>()
        val externalIds = mutableMapOf<String, Int>()
        for ((rowNumber, fields) in rows) {
            val rowIssues = mutableListOf<CollectionImportIssue>()
            fun raw(vararg names: String): Any? = names.asSequence().map(::norm).mapNotNull { fields[it] }.firstOrNull { it.toString().isNotBlank() }
            fun string(vararg names: String): String? = raw(*names)?.toString()?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) }
            fun int(field: String, vararg names: String): Int? = string(*names)?.toIntOrNull().also { parsed ->
                if (string(*names) != null && parsed == null) rowIssues += CollectionImportIssue(rowNumber, CollectionImportIssueCode.INVALID_VALUE, field, "$field must be an integer")
            }
            fun double(field: String, vararg names: String): Double? = string(*names)?.toDoubleOrNull().also { parsed ->
                if (string(*names) != null && parsed == null) rowIssues += CollectionImportIssue(rowNumber, CollectionImportIssueCode.INVALID_VALUE, field, "$field must be numeric")
            }
            fun bool(field: String, vararg names: String): Boolean = when (string(*names)?.lowercase()) {
                null, "", "false", "0", "no", "n" -> false
                "true", "1", "yes", "y" -> true
                else -> { rowIssues += CollectionImportIssue(rowNumber, CollectionImportIssueCode.INVALID_VALUE, field, "$field must be boolean"); false }
            }
            fun strings(vararg names: String): List<String> {
                val value = raw(*names) ?: return emptyList()
                return when (value) {
                    is List<*> -> value.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
                    else -> value.toString().split('|',';').map(String::trim).filter(String::isNotEmpty)
                }
            }

            val species = string("speciesId", "speciesid", "species")
            if (species == null) rowIssues += CollectionImportIssue(rowNumber, CollectionImportIssueCode.MISSING_REQUIRED_FIELD, "speciesId", "speciesId is required")
            val a = int("ivAttack", "ivattack", "attack")
            val d = int("ivDefense", "ivdefense", "defense")
            val s = int("ivStamina", "ivstamina", "stamina")
            if (a == null) rowIssues += CollectionImportIssue(rowNumber, CollectionImportIssueCode.MISSING_REQUIRED_FIELD, "ivAttack", "ivAttack is required")
            if (d == null) rowIssues += CollectionImportIssue(rowNumber, CollectionImportIssueCode.MISSING_REQUIRED_FIELD, "ivDefense", "ivDefense is required")
            if (s == null) rowIssues += CollectionImportIssue(rowNumber, CollectionImportIssueCode.MISSING_REQUIRED_FIELD, "ivStamina", "ivStamina is required")
            listOf("ivAttack" to a, "ivDefense" to d, "ivStamina" to s).forEach { (name, value) ->
                if (value != null && value !in 0..15) rowIssues += CollectionImportIssue(rowNumber, CollectionImportIssueCode.INVALID_VALUE, name, "$name must be 0..15")
            }
            val cp = int("cp", "cp")
            if (cp != null && cp <= 0) rowIssues += CollectionImportIssue(rowNumber, CollectionImportIssueCode.INVALID_VALUE, "cp", "cp must be > 0")
            val level = double("level", "level")
            if (level != null && (level !in 1.0..51.0 || level * 2 != (level * 2).toInt().toDouble())) rowIssues += CollectionImportIssue(rowNumber, CollectionImportIssueCode.INVALID_VALUE, "level", "level must be 1..51 in 0.5 steps")
            val shadow = bool("isShadow", "isshadow", "shadow")
            val purified = bool("isPurified", "ispurified", "purified")
            if (shadow && purified) rowIssues += CollectionImportIssue(rowNumber, CollectionImportIssueCode.INVALID_VALUE, "isShadow/isPurified", "Pokémon cannot be Shadow and Purified simultaneously")
            val charged = strings("chargedmoveids", "chargedmoves")
            if (charged.size > 2) rowIssues += CollectionImportIssue(rowNumber, CollectionImportIssueCode.INVALID_VALUE, "chargedMoveIds", "At most two Charged Moves are supported")
            val externalId = string("externalId", "externalid")
            if (externalId != null) {
                val previous = externalIds.putIfAbsent(externalId, rowNumber)
                if (previous != null) rowIssues += CollectionImportIssue(rowNumber, CollectionImportIssueCode.DUPLICATE_EXTERNAL_ID_IN_BATCH, "externalId", "externalId '$externalId' duplicates row $previous")
            }
            issues += rowIssues
            if (rowIssues.any { it.code != CollectionImportIssueCode.UNSUPPORTED_FIELD }) continue

            val uncertain = linkedSetOf<OwnedPokemonField>()
            if (cp == null) uncertain += OwnedPokemonField.CP
            if (level == null) uncertain += OwnedPokemonField.LEVEL
            if (string("fastmoveid", "fastmove") == null) uncertain += OwnedPokemonField.FAST_MOVE
            if (charged.isEmpty()) uncertain += OwnedPokemonField.CHARGED_MOVES
            val rawStringMap = fields.mapValues { (_, v) ->
                when (v) { is List<*> -> v.joinToString("|") { it?.toString().orEmpty() }; null -> ""; else -> v.toString() }
            }
            val id = "import-${slug(sourceLabel)}-$rowNumber-${hash(rawStringMap.toSortedMap().entries.joinToString("|") { "${it.key}=${it.value}" }).take(10)}"
            val candidate = OwnedPokemon(
                id = id,
                speciesId = species!!,
                iv = IvSpread(a!!, d!!, s!!),
                cp = cp,
                level = level,
                nickname = string("nickname", "nickname"),
                isShadow = shadow,
                isPurified = purified,
                isShiny = bool("isShiny", "isshiny", "shiny"),
                isLucky = bool("isLucky", "islucky", "lucky"),
                isBestBuddy = bool("isBestBuddy", "isbestbuddy", "bestbuddy"),
                isFavorite = bool("isFavorite", "isfavorite", "favorite"),
                fastMoveId = string("fastMoveId", "fastmoveid", "fastmove"),
                chargedMoveIds = charged,
                secondChargedMoveUnlocked = string("secondchargedmoveunlocked")?.let { bool("secondChargedMoveUnlocked", "secondchargedmoveunlocked") }
                    ?: charged.takeIf { it.size == 2 }?.let { true },
                tags = strings("tags").toSet(),
                caughtAtEpochMs = string("caughtatepochms")?.toLongOrNull(),
                createdAtEpochMs = importedAtEpochMs,
                updatedAtEpochMs = importedAtEpochMs,
                externalSource = sourceLabel,
                externalId = externalId,
                lastVerifiedAtEpochMs = importedAtEpochMs,
                verificationSource = CollectionVerificationSource.IMPORT,
                uncertainFields = uncertain
            )
            candidates += CollectionImportCandidate(rowNumber, candidate, sourceLabel, externalId, rawStringMap)
        }
        return CollectionImportBatch(format, sourceLabel, importedAtEpochMs, candidates, issues, rows.size)
    }

    private fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> { row += cell.toString(); cell.setLength(0) }
                (ch == '\n' || ch == '\r') && !quoted -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += cell.toString(); cell.setLength(0)
                    if (row.any(String::isNotBlank)) rows += row
                    row = mutableListOf()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            if (row.any(String::isNotBlank)) rows += row
        }
        require(!quoted) { "Unterminated quoted CSV field" }
        return rows
    }

    private fun norm(s: String): String = s.trim().lowercase().replace("_", "").replace("-", "").replace(" ", "")
    private fun slug(s: String): String = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "source" }
    private fun hash(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

private class JsonMiniParser(private val text: String) {
    private var i = 0
    fun parse(): Any? { skip(); val v = value(); skip(); require(i == text.length) { "Trailing JSON data" }; return v }
    private fun value(): Any? { skip(); require(i < text.length) { "Unexpected end of JSON" }; return when (text[i]) {
        '{' -> obj(); '[' -> arr(); '"' -> str(); 't' -> literal("true", true); 'f' -> literal("false", false); 'n' -> literal("null", null); else -> num()
    } }
    private fun obj(): Map<String, Any?> { expect('{'); skip(); val out=linkedMapOf<String,Any?>(); if (peek('}')) { i++; return out }; while(true){ val k=str(); skip(); expect(':'); out[k]=value(); skip(); if(peek('}')){i++;return out}; expect(',') } }
    private fun arr(): List<Any?> { expect('['); skip(); val out=mutableListOf<Any?>(); if(peek(']')){i++;return out}; while(true){ out+=value(); skip(); if(peek(']')){i++;return out}; expect(',') } }
    private fun str(): String { expect('"'); val b=StringBuilder(); while(i<text.length){ val c=text[i++]; if(c=='"') return b.toString(); if(c=='\\'){ require(i<text.length); when(val e=text[i++]){ '"','\\','/'->b.append(e); 'b'->b.append('\b'); 'f'->b.append('\u000C'); 'n'->b.append('\n'); 'r'->b.append('\r'); 't'->b.append('\t'); 'u'->{ require(i+4<=text.length); b.append(text.substring(i,i+4).toInt(16).toChar()); i+=4 }; else->error("Invalid JSON escape") } } else b.append(c) }; error("Unterminated JSON string") }
    private fun num(): Number { val start=i; if(peek('-')) i++; while(i<text.length&&text[i].isDigit()) i++; if(i<text.length&&text[i]=='.'){i++;while(i<text.length&&text[i].isDigit())i++}; if(i<text.length&&(text[i]=='e'||text[i]=='E')){i++;if(i<text.length&&(text[i]=='+'||text[i]=='-'))i++;while(i<text.length&&text[i].isDigit())i++}; val s=text.substring(start,i); return s.toLongOrNull() ?: s.toDoubleOrNull() ?: error("Invalid JSON number") }
    private fun <T> literal(word:String,v:T):T { require(text.startsWith(word,i)); i+=word.length; return v }
    private fun skip(){ while(i<text.length&&text[i].isWhitespace()) i++ }
    private fun expect(c:Char){ skip(); require(i<text.length&&text[i]==c){"Expected '$c' at $i"}; i++ }
    private fun peek(c:Char)=i<text.length&&text[i]==c
}
