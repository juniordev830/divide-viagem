import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.util.Base64

data class Participant(val name: String, val weight: Int = 1)

data class LedgerExpense(
    val id: String,
    val payer: String,
    val cents: Long,
    val beneficiaries: List<String>,
    val weights: Map<String, Int> = emptyMap(),
    val category: String = "general",
    val note: String = ""
)

data class ParticipantBalance(
    val name: String,
    val paidCents: Long,
    val owedCents: Long,
    val netCents: Long
)

data class LedgerSnapshot(
    val expenseCount: Int,
    val totalCents: Long,
    val balances: List<ParticipantBalance>,
    val categoryTotals: Map<String, Long>,
    val transfers: List<Transfer>
)

/** Offline weighted expense ledger: no floating-point money or actual payments. */
class TravelLedger(participants: List<Participant>) {
    val members: List<Participant> = participants.toList()
    private val byName: Map<String, Participant>
    private val entries = linkedMapOf<String, LedgerExpense>()

    init {
        require(members.isNotEmpty() && members.size <= 100) {
            "A viagem precisa de 1 a 100 participantes"
        }
        require(members.all { it.name.isNotBlank() && it.name.length <= 80 && it.weight > 0 }) {
            "Participante ou peso inválido"
        }
        require(members.map { it.name }.distinct().size == members.size) {
            "Participantes duplicados"
        }
        byName = members.associateBy { it.name }
    }

    fun listExpenses(): List<LedgerExpense> = entries.values.toList()

    fun expense(id: String): LedgerExpense? = entries[id]

    fun addExpense(expense: LedgerExpense) {
        validateExpense(expense)
        require(!entries.containsKey(expense.id)) { "Identificador de despesa duplicado" }
        require(entries.size < 10000) { "Limite de despesas atingido" }
        entries[expense.id] = expense.copy(
            beneficiaries = expense.beneficiaries.toList(),
            weights = expense.weights.toMap()
        )
    }

    fun replaceExpense(id: String, replacement: LedgerExpense) {
        require(entries.containsKey(id)) { "Despesa não encontrada" }
        require(id == replacement.id) { "Identificador não pode ser alterado" }
        validateExpense(replacement)
        entries[id] = replacement.copy(
            beneficiaries = replacement.beneficiaries.toList(),
            weights = replacement.weights.toMap()
        )
    }

    fun removeExpense(id: String): LedgerExpense? = entries.remove(id)

    fun expensesFor(name: String): List<LedgerExpense> {
        require(name in byName) { "Participante desconhecido" }
        return entries.values.filter { it.payer == name || name in it.beneficiaries }
    }

    fun expensesByCategory(category: String): List<LedgerExpense> =
        entries.values.filter { it.category == category }

    private fun validateExpense(expense: LedgerExpense) {
        require(expense.id.isNotBlank() && expense.id.length <= 100) { "ID inválido" }
        require(expense.payer in byName) { "Pagador desconhecido" }
        require(expense.cents >= 0) { "Valor negativo" }
        require(expense.beneficiaries.isNotEmpty()) { "Sem beneficiários" }
        require(expense.beneficiaries.size == expense.beneficiaries.distinct().size) {
            "Beneficiários duplicados"
        }
        require(expense.beneficiaries.all { it in byName }) { "Beneficiário desconhecido" }
        require(expense.weights.keys.all { it in expense.beneficiaries }) {
            "Peso para pessoa fora da despesa"
        }
        require(expense.weights.values.all { it > 0 }) { "Peso inválido" }
        require(expense.category.isNotBlank() && expense.category.length <= 80) {
            "Categoria inválida"
        }
        require(expense.note.length <= 1000) { "Nota muito longa" }
    }

    /** Largest-remainder allocation, stable in beneficiary order. */
    fun shares(expense: LedgerExpense): Map<String, Long> {
        validateExpense(expense)
        val weights = expense.beneficiaries.map { name ->
            expense.weights[name] ?: byName.getValue(name).weight
        }
        val totalWeight = weights.fold(BigInteger.ZERO) { acc, weight ->
            acc.add(BigInteger.valueOf(weight.toLong()))
        }
        val amount = BigInteger.valueOf(expense.cents)
        val allocated = MutableList(weights.size) { 0L }
        val remainders = mutableListOf<Pair<Int, BigInteger>>()
        var distributed = 0L

        for (index in weights.indices) {
            val numerator = amount.multiply(BigInteger.valueOf(weights[index].toLong()))
            val qr = numerator.divideAndRemainder(totalWeight)
            allocated[index] = qr[0].longValueExact()
            distributed = Math.addExact(distributed, allocated[index])
            remainders += index to qr[1]
        }

        var remaining = Math.subtractExact(expense.cents, distributed)
        for ((index, _) in remainders.sortedWith(
            compareByDescending<Pair<Int, BigInteger>> { it.second }.thenBy { it.first }
        )) {
            if (remaining == 0L) break
            allocated[index] = Math.addExact(allocated[index], 1L)
            remaining--
        }
        check(remaining == 0L)
        return expense.beneficiaries.indices.associate { index ->
            expense.beneficiaries[index] to allocated[index]
        }
    }

    fun balances(): List<ParticipantBalance> {
        val paid = members.associate { it.name to 0L }.toMutableMap()
        val owed = members.associate { it.name to 0L }.toMutableMap()
        for (entry in entries.values) {
            paid[entry.payer] = Math.addExact(paid.getValue(entry.payer), entry.cents)
            for ((name, share) in shares(entry)) {
                owed[name] = Math.addExact(owed.getValue(name), share)
            }
        }
        val result = members.map { member ->
            val name = member.name
            ParticipantBalance(
                name,
                paid.getValue(name),
                owed.getValue(name),
                Math.subtractExact(paid.getValue(name), owed.getValue(name))
            )
        }
        check(result.fold(0L) { total, item -> Math.addExact(total, item.netCents) } == 0L)
        return result
    }

    /** Greedy deterministic transfers, without moving any money. */
    fun settlements(): List<Transfer> {
        val balances = balances()
        val debtors = balances.filter { it.netCents < 0 }.map {
            it.name to -it.netCents
        }.toMutableList()
        val creditors = balances.filter { it.netCents > 0 }.map {
            it.name to it.netCents
        }.toMutableList()
        val transfers = mutableListOf<Transfer>()
        var debtorIndex = 0
        var creditorIndex = 0

        while (debtorIndex < debtors.size && creditorIndex < creditors.size) {
            val debtor = debtors[debtorIndex]
            val creditor = creditors[creditorIndex]
            val amount = minOf(debtor.second, creditor.second)
            check(amount > 0L)
            transfers += Transfer(debtor.first, creditor.first, amount)
            debtors[debtorIndex] = debtor.first to (debtor.second - amount)
            creditors[creditorIndex] = creditor.first to (creditor.second - amount)
            if (debtors[debtorIndex].second == 0L) debtorIndex++
            if (creditors[creditorIndex].second == 0L) creditorIndex++
        }
        check(debtors.all { it.second == 0L } && creditors.all { it.second == 0L })
        check(reconciles(transfers))
        return transfers
    }

    fun reconciles(transfers: List<Transfer>): Boolean {
        val remaining = balances().associate { it.name to it.netCents }.toMutableMap()
        for (transfer in transfers) {
            if (transfer.from !in remaining || transfer.to !in remaining ||
                transfer.from == transfer.to || transfer.cents <= 0L) return false
            try {
                remaining[transfer.from] =
                    Math.addExact(remaining.getValue(transfer.from), transfer.cents)
                remaining[transfer.to] =
                    Math.subtractExact(remaining.getValue(transfer.to), transfer.cents)
            } catch (_: ArithmeticException) {
                return false
            }
        }
        return remaining.values.all { it == 0L }
    }

    fun categoryTotals(): Map<String, Long> {
        val totals = sortedMapOf<String, Long>()
        for (entry in entries.values) {
            totals[entry.category] = Math.addExact(totals[entry.category] ?: 0L, entry.cents)
        }
        return totals
    }

    fun snapshot(): LedgerSnapshot {
        val total = entries.values.fold(0L) { acc, entry ->
            Math.addExact(acc, entry.cents)
        }
        return LedgerSnapshot(
            expenseCount = entries.size,
            totalCents = total,
            balances = balances(),
            categoryTotals = categoryTotals(),
            transfers = settlements()
        )
    }

    fun preview(expense: LedgerExpense): LedgerSnapshot {
        val copy = TravelLedger(members)
        for (existing in entries.values) copy.addExpense(existing)
        copy.addExpense(expense)
        return copy.snapshot()
    }

    fun toTsv(): String {
        val lines = mutableListOf("ledger-v1")
        for (person in members) {
            lines += listOf("participant", encode(person.name), person.weight.toString())
                .joinToString("\t")
        }
        for (entry in entries.values) {
            val beneficiaries = entry.beneficiaries.joinToString(",") { encode(it) }
            val weights = entry.weights.toSortedMap().entries.joinToString(",") {
                encode(it.key) + ":" + it.value
            }
            lines += listOf(
                "expense", encode(entry.id), encode(entry.payer), entry.cents.toString(),
                beneficiaries, weights, encode(entry.category), encode(entry.note)
            ).joinToString("\t")
        }
        return lines.joinToString("\n", postfix = "\n")
    }

    companion object {
        private val encoder = Base64.getUrlEncoder().withoutPadding()
        private val decoder = Base64.getUrlDecoder()

        private fun encode(value: String): String =
            encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

        private fun decode(value: String): String =
            String(decoder.decode(value), StandardCharsets.UTF_8)

        fun fromTsv(text: String): TravelLedger {
            val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
            require(lines.firstOrNull() == "ledger-v1") { "Versão TSV desconhecida" }
            val participants = mutableListOf<Participant>()
            val expenseLines = mutableListOf<List<String>>()
            var readingExpenses = false
            for (line in lines.drop(1)) {
                val fields = line.split("\t")
                when (fields.firstOrNull()) {
                    "participant" -> {
                        require(!readingExpenses && fields.size == 3) {
                            "Registro de participante inválido"
                        }
                        participants += Participant(decode(fields[1]), fields[2].toInt())
                    }
                    "expense" -> {
                        readingExpenses = true
                        require(fields.size == 8) { "Registro de despesa inválido" }
                        expenseLines += fields
                    }
                    else -> throw IllegalArgumentException("Registro TSV desconhecido")
                }
            }
            val ledger = TravelLedger(participants)
            for (fields in expenseLines) {
                val beneficiaries = fields[4].split(",").map { decode(it) }
                val weights = if (fields[5].isEmpty()) emptyMap() else {
                    fields[5].split(",").associate { token ->
                        val parts = token.split(":", limit = 2)
                        require(parts.size == 2) { "Peso TSV inválido" }
                        decode(parts[0]) to parts[1].toInt()
                    }
                }
                ledger.addExpense(LedgerExpense(
                    id = decode(fields[1]),
                    payer = decode(fields[2]),
                    cents = fields[3].toLong(),
                    beneficiaries = beneficiaries,
                    weights = weights,
                    category = decode(fields[6]),
                    note = decode(fields[7])
                ))
            }
            return ledger
        }
    }
}

fun parseCents(decimal: String): Long {
    val value = BigDecimal(decimal.trim())
    require(value.signum() >= 0) { "Valor negativo" }
    return value.setScale(2, RoundingMode.UNNECESSARY)
        .movePointRight(2).longValueExact()
}

fun formatCents(cents: Long): String =
    BigDecimal.valueOf(cents, 2).setScale(2).toPlainString()

fun renderLedger(snapshot: LedgerSnapshot): String {
    val lines = mutableListOf(
        "Despesas: " + snapshot.expenseCount + " | Total: " + formatCents(snapshot.totalCents),
        "Participantes:"
    )
    for (item in snapshot.balances) {
        lines += item.name + ": pago=" + formatCents(item.paidCents) +
            " devido=" + formatCents(item.owedCents) +
            " saldo=" + formatCents(item.netCents)
    }
    lines += "Categorias:"
    for ((category, amount) in snapshot.categoryTotals) {
        lines += category + ": " + formatCents(amount)
    }
    lines += "Transferências sugeridas:"
    for (transfer in snapshot.transfers) {
        lines += transfer.from + " -> " + transfer.to + ": " + formatCents(transfer.cents)
    }
    return lines.joinToString("\n", postfix = "\n")
}
