/** Executable domain scenarios; no third-party test runner required. */
fun runLedgerTests(): Int {
    var passed = 0
    fun scenario(name: String, block: () -> Unit) {
        try {
            block()
            passed++
        } catch (error: Throwable) {
            throw AssertionError("Falha no cenário: " + name, error)
        }
    }
    fun fails(block: () -> Unit) {
        check(runCatching(block).isFailure) { "Operação inválida deveria falhar" }
    }
    fun trio(): TravelLedger = TravelLedger(
        listOf(Participant("Ana"), Participant("Bia"), Participant("Caio"))
    )
    fun expense(
        cents: Long = 100,
        payer: String = "Ana",
        beneficiaries: List<String> = listOf("Ana", "Bia", "Caio")
    ) = LedgerExpense("e1", payer, cents, beneficiaries)

    scenario("participantes não podem ser vazios") {
        fails { TravelLedger(emptyList()) }
    }
    scenario("nomes duplicados são rejeitados") {
        fails { TravelLedger(listOf(Participant("Ana"), Participant("Ana"))) }
    }
    scenario("pesos inválidos são rejeitados") {
        fails { TravelLedger(listOf(Participant("Ana", 0))) }
        fails { TravelLedger(listOf(Participant("Ana", -1))) }
    }
    scenario("nomes vazios são rejeitados") {
        fails { TravelLedger(listOf(Participant(" "))) }
    }
    scenario("limite de participantes") {
        fails { TravelLedger((1..101).map { Participant("P" + it) }) }
    }
    scenario("divisão igual preserva centavos") {
        val ledger = trio()
        check(ledger.shares(expense()) == mapOf("Ana" to 34L, "Bia" to 33L, "Caio" to 33L))
    }
    scenario("um centavo não se perde") {
        val ledger = trio()
        check(ledger.shares(expense(1)) == mapOf("Ana" to 1L, "Bia" to 0L, "Caio" to 0L))
    }
    scenario("zero centavos distribui zero") {
        check(trio().shares(expense(0)).values.all { it == 0L })
    }
    scenario("pesos padrão dos participantes") {
        val ledger = TravelLedger(
            listOf(Participant("Ana", 1), Participant("Bia", 2), Participant("Caio", 3))
        )
        check(ledger.shares(expense()) == mapOf("Ana" to 17L, "Bia" to 33L, "Caio" to 50L))
    }
    scenario("pesos específicos substituem o padrão") {
        val ledger = trio()
        val item = expense().copy(weights = mapOf("Ana" to 2, "Bia" to 1, "Caio" to 1))
        check(ledger.shares(item) == mapOf("Ana" to 50L, "Bia" to 25L, "Caio" to 25L))
    }
    scenario("pagador pode não ser beneficiário") {
        val ledger = trio()
        val item = expense(100, "Ana", listOf("Bia", "Caio"))
        ledger.addExpense(item)
        check(ledger.balances().first().netCents == 100L)
        check(ledger.settlements().size == 2)
    }
    scenario("beneficiário desconhecido é rejeitado") {
        fails { trio().addExpense(expense().copy(beneficiaries = listOf("Dora"))) }
    }
    scenario("pagador desconhecido é rejeitado") {
        fails { trio().addExpense(expense().copy(payer = "Dora")) }
    }
    scenario("beneficiários duplicados são rejeitados") {
        fails { trio().addExpense(expense().copy(beneficiaries = listOf("Ana", "Ana"))) }
    }
    scenario("despesa precisa de beneficiários") {
        fails { trio().addExpense(expense().copy(beneficiaries = emptyList())) }
    }
    scenario("peso de pessoa ausente é rejeitado") {
        fails { trio().addExpense(expense().copy(weights = mapOf("Dora" to 2))) }
    }
    scenario("peso zero é rejeitado") {
        fails { trio().addExpense(expense().copy(weights = mapOf("Ana" to 0))) }
    }
    scenario("despesa negativa é rejeitada") {
        fails { trio().addExpense(expense(-1)) }
    }
    scenario("ID vazio é rejeitado") {
        fails { trio().addExpense(expense().copy(id = "")) }
    }
    scenario("categoria vazia é rejeitada") {
        fails { trio().addExpense(expense().copy(category = " ")) }
    }
    scenario("IDs duplicados não sobrescrevem dados") {
        val ledger = trio()
        ledger.addExpense(expense())
        fails { ledger.addExpense(expense(200)) }
        check(ledger.expense("e1")?.cents == 100L)
    }
    scenario("saldo de três pessoas é reconciliado") {
        val ledger = trio()
        ledger.addExpense(expense())
        check(ledger.balances().map { it.netCents } == listOf(66L, -33L, -33L))
        check(ledger.settlements() == listOf(
            Transfer("Bia", "Ana", 33),
            Transfer("Caio", "Ana", 33)
        ))
    }
    scenario("pagamentos cruzados geram transferências válidas") {
        val ledger = trio()
        ledger.addExpense(expense(300))
        ledger.addExpense(expense(90, "Bia").copy(id = "e2"))
        check(ledger.reconciles(ledger.settlements()))
    }
    scenario("reconciliação rejeita transferência falsa") {
        val ledger = trio()
        ledger.addExpense(expense())
        check(!ledger.reconciles(listOf(Transfer("Bia", "Ana", 32))))
    }
    scenario("reconciliação rejeita participantes desconhecidos") {
        val ledger = trio()
        ledger.addExpense(expense())
        check(!ledger.reconciles(listOf(Transfer("Dora", "Ana", 10))))
    }
    scenario("reconciliação rejeita auto-transferência") {
        check(!trio().reconciles(listOf(Transfer("Ana", "Ana", 1))))
    }
    scenario("substituição atualiza despesa existente") {
        val ledger = trio()
        ledger.addExpense(expense())
        ledger.replaceExpense("e1", expense(300))
        check(ledger.snapshot().totalCents == 300L)
    }
    scenario("substituição inválida não altera despesa") {
        val ledger = trio()
        ledger.addExpense(expense())
        fails { ledger.replaceExpense("e1", expense(-1)) }
        check(ledger.expense("e1")?.cents == 100L)
    }
    scenario("substituição não muda identificador") {
        val ledger = trio()
        ledger.addExpense(expense())
        fails { ledger.replaceExpense("e1", expense().copy(id = "e2")) }
    }
    scenario("remoção é reversível por novo cadastro") {
        val ledger = trio()
        ledger.addExpense(expense())
        check(ledger.removeExpense("e1") != null)
        check(ledger.removeExpense("e1") == null)
        ledger.addExpense(expense())
        check(ledger.listExpenses().size == 1)
    }
    scenario("consulta por pessoa considera pagador e beneficiário") {
        val ledger = trio()
        ledger.addExpense(expense(100, "Ana", listOf("Bia")))
        check(ledger.expensesFor("Ana").size == 1)
        check(ledger.expensesFor("Bia").size == 1)
        check(ledger.expensesFor("Caio").isEmpty())
    }
    scenario("consulta de pessoa desconhecida falha") {
        fails { trio().expensesFor("Dora") }
    }
    scenario("categorias somam sem arredondamento") {
        val ledger = trio()
        ledger.addExpense(expense().copy(category = "alimentação"))
        ledger.addExpense(expense(50).copy(id = "e2", category = "alimentação"))
        check(ledger.categoryTotals()["alimentação"] == 150L)
        check(ledger.expensesByCategory("alimentação").size == 2)
    }
    scenario("preview não altera a viagem original") {
        val ledger = trio()
        ledger.addExpense(expense())
        val future = ledger.preview(expense(50).copy(id = "e2"))
        check(future.totalCents == 150L)
        check(ledger.snapshot().totalCents == 100L)
    }
    scenario("TSV preserva Unicode e caracteres especiais") {
        val ledger = TravelLedger(listOf(Participant("João"), Participant("Ana\tBia")))
        ledger.addExpense(LedgerExpense(
            "café", "João", 333, listOf("João", "Ana\tBia"),
            category = "refeições", note = "linha 1\nlinha 2"
        ))
        val restored = TravelLedger.fromTsv(ledger.toTsv())
        check(restored.members == ledger.members)
        check(restored.listExpenses() == ledger.listExpenses())
        check(restored.snapshot() == ledger.snapshot())
    }
    scenario("TSV vazio preserva participantes") {
        val ledger = trio()
        val restored = TravelLedger.fromTsv(ledger.toTsv())
        check(restored.members == ledger.members)
        check(restored.listExpenses().isEmpty())
    }
    scenario("TSV rejeita versão desconhecida") {
        fails { TravelLedger.fromTsv("ledger-v2\n") }
    }
    scenario("TSV rejeita registros desconhecidos") {
        fails { TravelLedger.fromTsv("ledger-v1\nunknown\tbad\n") }
    }
    scenario("TSV rejeita participantes após despesas") {
        val ledger = trio()
        ledger.addExpense(expense())
        val serialized = ledger.toTsv()
        fails { TravelLedger.fromTsv(serialized + "participant\tQW5h\t1\n") }
    }
    scenario("valores monetários usam BigDecimal") {
        check(parseCents("12.34") == 1234L)
        check(parseCents("0.01") == 1L)
        check(parseCents("0") == 0L)
    }
    scenario("não aceita arredondamento silencioso") {
        fails { parseCents("0.001") }
        fails { parseCents("-1") }
    }
    scenario("formatação monetária é exata") {
        check(formatCents(1234) == "12.34")
        check(formatCents(1) == "0.01")
    }
    scenario("overflow de despesas é detectado") {
        val ledger = trio()
        ledger.addExpense(expense(Long.MAX_VALUE))
        ledger.addExpense(expense(1).copy(id = "e2"))
        fails { ledger.snapshot() }
    }
    scenario("relatório mostra totais e sugestões") {
        val ledger = trio()
        ledger.addExpense(expense())
        val output = renderLedger(ledger.snapshot())
        check(output.contains("Despesas: 1"))
        check(output.contains("Ana"))
        check(output.contains("Bia -> Ana"))
    }
    scenario("grande valor é dividido sem multiplicação overflow") {
        val ledger = trio()
        val portions = ledger.shares(expense(Long.MAX_VALUE))
        check(portions.values.fold(0L) { total, value -> Math.addExact(total, value) }
            == Long.MAX_VALUE)
    }
    return passed
}
