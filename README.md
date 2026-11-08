# Divide Viagem

Divisão de despesas de viagem com cálculo do saldo por participante.

## Implementado na primeira versão

**Kotlin — núcleo executável de domínio.** Divide despesas em centavos inteiros e propõe transferências que zeram todos os saldos. O resto da divisão é atribuído aos primeiros participantes, na ordem informada.

## Como executar

Instale JDK 17+ e o compilador Kotlin 2.1.0 (ou compatível). Os comandos abaixo partem da raiz do repositório.

```sh
kotlinc Main.kt -include-runtime -d app.jar
java -jar app.jar Ana,Bia,Caio examples/despesas.tsv
```

## Testes

```sh
kotlinc Main.kt -include-runtime -d app.jar
java -jar app.jar --self-test
```

Os testes cobrem invariantes e entradas inválidas. A preparação local não dispõe deste runtime; o workflow em `.github/workflows/ci.yml` executa a validação no GitHub Actions. O resultado de cada execução está na aba Actions, associado ao commit.

## Organização e limites

Código e exemplos: `Main.kt e examples/despesas.tsv`. Os dados de exemplo são fictícios. As funções de domínio são separadas da leitura de argumentos no mesmo arquivo para manter a primeira versão pequena e revisável.

Divisão igual entre todos, sem moedas múltiplas ou pesos. As transferências são sugestões de cálculo e não movimentam dinheiro. Gradle e interface estão no roteiro.

A proposta completa de evolução está em `ARQUITETURA.md`. As tecnologias propostas nessa arquitetura não devem ser interpretadas como dependências já implementadas.

## Próximas entregas

- [ ] Cadastrar participantes e despesas — ampliar o núcleo atual até o fluxo completo.
- [ ] Dividir valores com arredondamento correto — ampliar o núcleo atual até o fluxo completo.
- [ ] Calcular saldos e exportar resumo — ampliar o núcleo atual até o fluxo completo.

Licença: ainda não definida.

## Livro de despesas ponderadas (nova implementação)

O fluxo original de divisão igual foi preservado. A nova classe `TravelLedger`
adiciona participantes com pesos, despesas com beneficiários selecionados,
pesos específicos por despesa, categorias, edição, remoção e simulações.

```sh
kotlinc Main.kt TravelLedger.kt TravelLedgerTests.kt -include-runtime -d app.jar
java -jar app.jar --self-test
java -jar app.jar --ledger-demo
java -jar app.jar --ledger-demo-tsv > viagem.tsv
java -jar app.jar --ledger-report viagem.tsv
```

Os valores são centavos inteiros (`Long`). A função `parseCents` usa `BigDecimal`
para interpretar valores como `12.34` sem perda de precisão. A divisão
ponderada utiliza `BigInteger` para evitar overflow intermediário e distribui
restos pelo método dos maiores resíduos, com desempate pela ordem dos
beneficiários. O saldo total é sempre zero e as transferências são apenas
**sugestões**; nenhum pagamento é realizado.

O formato TSV `ledger-v1` codifica campos de texto em Base64 URL-safe para
preservar nomes com acentos, tabulações e quebras de linha. É um formato
local de intercâmbio, não criptografia nem banco de dados. Arquivos externos
devem ser tratados como não confiáveis; registros e pesos inválidos falham
com erro em vez de alterar o livro de despesas.

A suíte de cenários executada por `--self-test` cobre arredondamento, pesos,
beneficiários, erros de entrada, reconciliação, operações reversíveis,
preview, exportação/importação TSV, Unicode, dinheiro e overflow.

**Limites:** até 100 participantes e 10 mil despesas por instância; não
há persistência automática, interface gráfica, autenticação ou integração
bancária. A edição é em memória e a exportação é feita explicitamente.
