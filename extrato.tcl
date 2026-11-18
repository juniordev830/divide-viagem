# Lê o TSV de --resumo e imprime o extrato em reais. Requer Tcl 8.6.
proc reais {centavos} {
    set valor [expr {abs($centavos)}]
    return [format {R$ %s,%02d} [expr {$valor / 100}] [expr {$valor % 100}]]
}

proc extrato {entrada} {
    set cabecalho "participante\tpago_centavos\tcota_centavos\tsaldo_centavos"
    if {[gets $entrada linha] < 0 || $linha ne $cabecalho} {
        error "esperado o cabeçalho gerado por --resumo"
    }
    set resultado {}
    set totalPago 0
    set totalSaldo 0
    set pessoas [dict create]
    while {[gets $entrada linha] >= 0} {
        set campos [split $linha \t]
        lassign $campos nome pago cota saldo
        if {[llength $campos] != 4 || [string trim $nome] eq "" || [dict exists $pessoas $nome]} {
            error "linha de participante inválida ou repetida"
        }
        foreach valor [list $pago $cota $saldo] {
            if {![regexp {^(0|-?[1-9][0-9]*)$} $valor]} {
                error "centavos devem ser inteiros decimais"
            }
        }
        if {$pago < 0 || $cota < 0 || $saldo != $pago - $cota} {
            error "saldo incompatível com o valor pago e a cota de $nome"
        }
        dict set pessoas $nome 1
        incr totalPago $pago
        incr totalSaldo $saldo
        if {$saldo > 0} {
            set situacao "recebe [reais $saldo]"
        } elseif {$saldo < 0} {
            set situacao "paga [reais $saldo]"
        } else {
            set situacao "em dia"
        }
        lappend resultado "$nome — pagou [reais $pago], cota [reais $cota], $situacao"
    }
    if {$totalSaldo != 0 || ![llength $resultado]} {
        error "resumo incompleto ou com saldos em aberto"
    }
    lappend resultado "Total da viagem — [reais $totalPago]"
    return [join $resultado \n]
}

fconfigure stdin -encoding utf-8 -translation auto
fconfigure stdout -encoding utf-8
if {[catch {extrato stdin} resultado]} {
    puts stderr "Erro no resumo: $resultado"
    exit 2
}
puts $resultado
