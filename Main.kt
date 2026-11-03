import java.io.File

data class Expense(val payer: String, val cents: Long)
data class Transfer(val from: String, val to: String, val cents: Long)
data class PersonBalance(val person: String, val paidCents: Long, val shareCents: Long, val balanceCents: Long)

/**
 * A positive balance is credit to receive; a negative balance is debt to pay.
 * An indivisible cent is allocated to the earliest participants in the given order.
 */
fun ledger(people: List<String>, expenses: List<Expense>): List<PersonBalance> {
    require(people.isNotEmpty() && people.all { it.isNotBlank() } &&
            people.distinct().size == people.size) { "Participantes inválidos" }
    val paid = people.associateWith { 0L }.toMutableMap()
    var total = 0L
    for (expense in expenses) {
        require(expense.payer in paid && expense.cents >= 0L) { "Despesa inválida" }
        total = Math.addExact(total, expense.cents)
        paid[expense.payer] = Math.addExact(paid.getValue(expense.payer), expense.cents)
    }
    return people.mapIndexed { index, person ->
        val share = total / people.size + if (index.toLong() < total % people.size) 1L else 0L
        val value = paid.getValue(person)
        PersonBalance(person, value, share, Math.subtractExact(value, share))
    }
}

/** Suggested transfers; never sends or moves money. */
fun settle(people: List<String>, expenses: List<Expense>): List<Transfer> {
    val balances = ledger(people, expenses).map { it.balanceCents }.toMutableList()
    val transfers = mutableListOf<Transfer>()
    var debtor = 0; var creditor = 0
    while (true) {
        while (debtor < balances.size && balances[debtor] >= 0L) debtor++
        while (creditor < balances.size && balances[creditor] <= 0L) creditor++
        if (debtor == balances.size || creditor == balances.size) break
        val cents = minOf(-balances[debtor], balances[creditor])
        transfers += Transfer(people[debtor], people[creditor], cents)
        balances[debtor] += cents
        balances[creditor] -= cents
    }
    check(balances.all { it == 0L })
    return transfers
}

fun main(args: Array<String>) {
    try {
        if (args.contentEquals(arrayOf("--self-test"))) {
            val people = listOf("Ana", "Bia", "Caio")
            val expenses = listOf(Expense("Ana", 100L))
            val transfers = settle(people, expenses)
            check(transfers == listOf(Transfer("Bia","Ana",33L), Transfer("Caio","Ana",33L)))
            check(ledger(people, expenses) == listOf(
                PersonBalance("Ana",100L,34L,66L),
                PersonBalance("Bia",0L,33L,-33L),
                PersonBalance("Caio",0L,33L,-33L)
            ))
            check(ledger(people, expenses).sumOf { it.balanceCents } == 0L)
            check(ledger(listOf("Ana"), listOf(Expense("Ana",99L))) ==
                listOf(PersonBalance("Ana",99L,99L,0L)))
            check(settle(listOf("Ana"), listOf(Expense("Ana",99L))).isEmpty())
            check(ledger(people, emptyList()).all { it.balanceCents == 0L })
            check(runCatching { settle(listOf("Ana"), listOf(Expense("Bia",1L))) }.isFailure)
            check(runCatching { settle(listOf("Ana"), listOf(Expense("Ana",Long.MAX_VALUE),Expense("Ana",1L))) }.isFailure)
            check(runCatching { ledger(listOf("Ana","Ana"), emptyList()) }.isFailure)
            println("9 cenários OK")
            return
        }
        require(args.size == 2 || (args.size == 3 && args[2] == "--resumo")) {
            "Uso: java -jar app.jar Ana,Bia,Caio despesas.tsv [--resumo]"
        }
        val rows = File(args[1]).readLines().filter { it.isNotBlank() }
        require(rows.firstOrNull() == "pagador\tcentavos") { "Cabeçalho TSV inválido" }
        val expenses = rows.drop(1).map {
            val c = it.split('\t')
            require(c.size == 2) { "Esperadas duas colunas" }
            Expense(c[0], c[1].toLong())
        }
        val people = args[0].split(',')
        if (args.size == 3) {
            for (entry in ledger(people, expenses)) {
                println("saldo\t${entry.person}\tpago=${entry.paidCents}\tcota=${entry.shareCents}\tsaldo=${entry.balanceCents} centavos")
            }
        }
        for (transfer in settle(people, expenses)) {
            println("${transfer.from}\t${transfer.to}\t${transfer.cents} centavos")
        }
    } catch (e: Exception) {
        System.err.println("Erro: ${e.message}")
        kotlin.system.exitProcess(2)
    }
}
