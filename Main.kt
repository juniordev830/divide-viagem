import java.io.File
data class Expense(val payer: String, val cents: Long)
data class Transfer(val from: String, val to: String, val cents: Long)
fun settle(people: List<String>, expenses: List<Expense>): List<Transfer> {
    require(people.isNotEmpty() && people.all { it.isNotBlank() } && people.distinct().size == people.size) { "Participantes inválidos" }
    val paid = people.associateWith { 0L }.toMutableMap()
    var total = 0L
    for (expense in expenses) {
        require(expense.payer in paid && expense.cents >= 0) { "Despesa inválida" }
        total = Math.addExact(total, expense.cents)
        paid[expense.payer] = Math.addExact(paid.getValue(expense.payer), expense.cents)
    }
    val balances = people.mapIndexed { index, person ->
        paid.getValue(person) - (total / people.size + if (index.toLong() < total % people.size) 1L else 0L)
    }.toMutableList()
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
            println("4 cenários originais + " + runLedgerTests() + " cenários de ledger OK"); return
        }
        require(args.size == 2) { "Uso: java -jar app.jar Ana,Bia,Caio despesas.tsv" }
        val rows = File(args[1]).readLines().filter { it.isNotBlank() }
        require(rows.firstOrNull() == "pagador\tcentavos") { "Cabeçalho TSV inválido" }
        val expenses = rows.drop(1).map { val c=it.split('\t'); require(c.size==2); Expense(c[0],c[1].toLong()) }
        for (t in settle(args[0].split(','), expenses)) println("${t.from}\t${t.to}\t${t.cents} centavos")
    } catch (e: Exception) { System.err.println("Erro: ${e.message}"); kotlin.system.exitProcess(2) }
}
