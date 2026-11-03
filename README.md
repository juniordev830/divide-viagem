# Divide Viagem

CLI em **Kotlin 2.1.0 / JDK 17+** para dividir despesas de viagem igualmente entre participantes e sugerir transferências entre eles.

## Funcionalidades

- `ledger(people, expenses)`: calcula o valor pago por pessoa, a cota igualitária e o saldo final. Saldo positivo significa **receber**; negativo significa **pagar**.
- `settle(people, expenses)`: gera uma lista de transferências sugeridas que zera os saldos, sem movimentar dinheiro.
- Todas as operações monetárias usam **centavos inteiros** (`Long`); `Math.addExact` / `Math.subtractExact` impedem estouro silencioso.
- O resto de uma divisão que não dá centavos exatos é atribuído às primeiras pessoas na ordem informada. Nomes duplicados, pagadores inexistentes e valores negativos são rejeitados.
- `--resumo` imprime primeiro a memória de cálculo de cada pessoa (`pago`, `cota`, `saldo`) e em seguida as transferências. Sem o parâmetro, mantém a saída antiga.

## Uso

```sh
kotlinc Main.kt -include-runtime -d app.jar
java -jar app.jar Ana,Bia,Caio examples/despesas.tsv
java -jar app.jar Ana,Bia,Caio examples/despesas.tsv --resumo
java -jar app.jar --self-test
```

O arquivo TSV deve começar por `pagador<TAB>centavos`. Os exemplos são fictícios. Para despesas totais de 100 centavos divididas por Ana, Bia e Caio, cada cota é de 34, 33 e 33 centavos, respectivamente.

## Testes

O GitHub Actions compila Kotlin e executa testes de arredondamento de centavos, balanço, transferência, lista vazia e overflow.

## Limitações

A divisão é sempre igual entre todos, sem pesos ou despesas pessoais, múltiplas moedas ou taxas. O programa não envia dinheiro, salva transferências ou oferece interface. Participantes são identificados pelos nomes exatos informados.

Licença ainda não definida.
