# ChargePlus — simulação de estação de recarga

Simulação de eventos discretos em Java com DESMO-J. O modelo representa chegadas de veículos, carregadores rápidos e convencionais, filas separadas ou compartilhadas e classes com prioridade.

## Cenários implementados

- Dimensionamento de carregadores rápidos e convencionais.
- Comparação entre filas FIFO e prioridade.
- Comparação entre filas separadas e fila única.
- Comparação do tempo de recarga convencional em cenários diferentes.
- 30 réplicas independentes por configuração, com sementes definidas.

Os tempos, probabilidades e horizonte de 840 minutos são parâmetros do modelo, não medições de uma estação real. Leia os comentários em `ChargePlus.java` antes de interpretar os resultados. O cenário horário citado no comentário do código ainda não está implementado.

## Requisitos e execução

Requer JDK (com `javac` e `java`) e a biblioteca DESMO-J 2.5.1e incluída como `desmoj-2.5.1e-bin.jar`.

No PowerShell, a partir desta pasta:

```powershell
javac -cp "desmoj-2.5.1e-bin.jar" ChargePlus.java
java -cp ".;desmoj-2.5.1e-bin.jar" ChargePlus
```

No macOS/Linux, use `:` no classpath no lugar de `;`:

```sh
javac -cp "desmoj-2.5.1e-bin.jar" ChargePlus.java
java -cp ".:desmoj-2.5.1e-bin.jar" ChargePlus
```

A compilação foi aprovada com JDK 25.0.1 após corrigir a chamada de API incompatível `setQueuePriority` para `setQueueingPriority`. A execução completa de fumaça também terminou com código 0; isso não valida estatisticamente os resultados. O repositório inclui também `desmoj-2.5.1e-src.zip`; confirme e preserve os avisos/licença da distribuição antes de redistribuir a biblioteca ou publicar o projeto. O `.gitignore` exclui `*.jar` e `*.zip`, embora esses arquivos sejam necessários para reproduzir o build; a origem/licença ainda precisa ser esclarecida e o empacotamento decidido. A licença do próprio projeto ainda não foi definida.

## Limitações conhecidas

- Métricas são médias de réplicas e não incluem intervalos de confiança.
- A prioridade é relevante nos cenários em que as classes compartilham fila; cenários com filas separadas não demonstram o efeito de disputa entre classes. Consulte as hipóteses no código.
- Veículos ainda em serviço no fim do horizonte não são relatados como métrica separada.
- Não há suíte de testes automatizados identificada.
