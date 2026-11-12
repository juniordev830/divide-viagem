# Guia técnico — Divide Viagem

## Estado atual

Núcleo Kotlin de divisão igual de despesas e sugestões de acerto; não transfere dinheiro.

## Referências de código

`Main.kt`, `examples/despesas.tsv` e `ARQUITETURA.md`.

## Validar mudanças

Consultar exemplos no README, compilar com `kotlinc Main.kt -include-runtime -d app.jar` e executar testes integrados indicados no projeto.

Usar somente dados fictícios e registrar comandos executados com resultados no PR. A mera presença de um teste no repositório não comprova que ele passou.

## Cenários de regressão

Validar centavos residuais, participantes duplicados, entrada negativa, conjunto vazio e soma das transferências consistente com despesas.

## Próximo incremento

Não oferecer transferência bancária. Próximo passo: documentar algoritmo de arredondamento e permitir pesos somente com testes de conservação de centavos.

## Critérios de revisão

- [ ] Código e documentação refletem o mesmo comportamento.
- [ ] Casos de fronteira estão protegidos por teste ou roteiro de validação.
- [ ] Arquivos e saídas geradas ficam fora do versionamento, quando contêm dados privados.
- [ ] Mudanças passam por branch e PR, sem integração automática.
