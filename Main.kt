import java.io.File
data class Expense(val payer: String, val cents: Long)
data class Transfer(val from: String, val to: String, val cents: Long)
data class ParticipantBalance(val person: String, val paid: Long, val share: Long, val balance: Long)
fun summarize(people: List<String>, expenses: List<Expense>): List<ParticipantBalance> {
    require(people.isNotEmpty() && people.all { it.isNotBlank() } && people.distinct().size == people.size) { "Participantes inválidos" }
    val paid = people.associateWith { 0L }.toMutableMap()
    var total = 0L
    for (expense in expenses) {
        require(expense.payer in paid && expense.cents >= 0) { "Despesa inválida" }
        total = Math.addExact(total, expense.cents)
        paid[expense.payer] = Math.addExact(paid.getValue(expense.payer), expense.cents)
    }
    return people.mapIndexed { index, person ->
        val share = total / people.size + if (index.toLong() < total % people.size) 1L else 0L
        ParticipantBalance(person, paid.getValue(person), share, paid.getValue(person) - share)
    }
}
fun settle(people: List<String>, expenses: List<Expense>): List<Transfer> {
    val balances = summarize(people, expenses).map { it.balance }.toMutableList()
    val transfers = mutableListOf<Transfer>()
    var debtor = 0; var creditor = 0
    while (true) {
        while (debtor < balances.size && balances[debtor] >= 0) debtor++
        while (creditor < balances.size && balances[creditor] <= 0) creditor++
        if (debtor == balances.size || creditor == balances.size) break
        val cents = minOf(-balances[debtor], balances[creditor])
        transfers += Transfer(people[debtor], people[creditor], cents)
        balances[debtor] += cents; balances[creditor] -= cents
    }
    check(balances.all { it == 0L }); return transfers
}
fun main(args: Array<String>) {
    try {
        if (args.contentEquals(arrayOf("--self-test"))) {
            val t = settle(listOf("Ana","Bia","Caio"), listOf(Expense("Ana", 100)))
            check(t == listOf(Transfer("Bia","Ana",33), Transfer("Caio","Ana",33)))
            check(settle(listOf("Ana"), listOf(Expense("Ana", 99))).isEmpty())
            check(runCatching { settle(listOf("Ana"), listOf(Expense("Bia", 1))) }.isFailure)
            check(runCatching { settle(listOf("Ana"), listOf(Expense("Ana", Long.MAX_VALUE),Expense("Ana",1))) }.isFailure)
            val summary = summarize(listOf("Ana", "Bia", "Caio"), listOf(Expense("Ana", 100)))
            check(summary == listOf(ParticipantBalance("Ana",100,34,66), ParticipantBalance("Bia",0,33,-33), ParticipantBalance("Caio",0,33,-33)))
            check(summary.sumOf { it.balance } == 0L)
            check(summarize(listOf("Ana","Bia"), emptyList()).all { it.balance == 0L && it.share == 0L })
            val split = summarize(listOf("Ana","Bia"), listOf(Expense("Ana",30),Expense("Ana",20),Expense("Bia",10)))
            check(split.map { it.paid } == listOf(50L,10L) && split.map { it.balance } == listOf(20L,-20L))
            check(summarize(listOf("Ana"),listOf(Expense("Ana",Long.MAX_VALUE))).single().balance == 0L)
            println("9 cenários OK"); return
        }
        val showSummary = args.firstOrNull() == "--resumo"
        val input = if (showSummary) args.drop(1) else args.toList()
        require(input.size == 2) { "Uso: java -jar app.jar [--resumo] Ana,Bia,Caio despesas.tsv" }
        val rows = File(input[1]).readLines().filter { it.isNotBlank() }
        require(rows.firstOrNull() == "pagador\tcentavos") { "Cabeçalho TSV inválido" }
        val expenses = rows.drop(1).map { val c=it.split('\t'); require(c.size==2); Expense(c[0],c[1].toLong()) }
        val people = input[0].split(',')
        if (showSummary) {
            require(people.none { name -> name.any { it == '\t' || it == '\r' || it == '\n' } }) { "Participante contém delimitador TSV" }
            val summary = summarize(people, expenses)
            println("participante\tpago_centavos\tcota_centavos\tsaldo_centavos")
            for (row in summary) println("${row.person}\t${row.paid}\t${row.share}\t${row.balance}")
        } else {
            for (t in settle(people, expenses)) println("${t.from}\t${t.to}\t${t.cents} centavos")
        }
    } catch (e: Exception) { System.err.println("Erro: ${e.message}"); kotlin.system.exitProcess(2) }
}

