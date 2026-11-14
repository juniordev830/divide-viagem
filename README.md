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

## Roteiro de qualidade

Consulte o [guia de validação e manutenção](docs/GUIA_DE_VALIDACAO.md) para reproduzir testes, entender as limitações e revisar mudanças com segurança.


## Resumo por participante

```sh
java -jar app.jar --resumo Ana,Bia,Caio examples/despesas.tsv
```

A saída TSV informa valor pago, cota e saldo em centavos, preservando a ordem dos participantes. Saldo positivo significa valor a receber; negativo, valor a pagar. O total dos saldos é zero. Para uma despesa de 100 centavos paga por Ana, as cotas de Ana, Bia e Caio são 34, 33 e 33 centavos. O comando anterior continua exibindo as transferências sugeridas.

A função `summarize(people, expenses)` pode ser usada diretamente por outra interface. Não grava dados nem realiza transferências. O modo TSV rejeita nomes com tabulações ou quebras de linha.
