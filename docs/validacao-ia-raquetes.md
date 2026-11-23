# Validação da IA de raquetes

## 1 - O problema

A rede anterior chegou à geração 100, mas fez 0 rebatidas em uma medição inicial de 16 partidas contra uma raquete parada. No Edge, a partida que já estava aberta mostrava vitória do jogador por 9 a 0. O erro do backpropagation podia ser baixo porque a maior parte dos movimentos recebia pouca informação sobre como corrigir a trajetória da raquete.

## 2 - O que mudou

A arquitetura continua com 9 entradas, duas camadas ocultas de 20 e 12 neurônios e 3 saídas. As redes continuam jogando umas contra as outras e a ação executada é a saída de maior valor. As saídas representam a qualidade de ir à esquerda, parar ou ir à direita. O treino tem duas etapas:

1. **Backpropagation.** Para cada estado coletado nos duelos, calculamos quanto cada ação aproximaria a raquete do ponto previsto de chegada da bola. Se a bola está indo para o outro lado, o alvo é o centro. Quando a raquete já está alinhada, ficar parada é valorizado. O motor ainda acrescenta um bônus de 0,2 à ação executada se houve rebatida e uma penalidade de 0,2 se sofreu ponto. Os três valores desejados são ajustados pelo mesmo backpropagation manual dos exercícios.
2. **Algoritmo genético.** Depois do treino, cada rede enfrenta dois adversários com troca de lados. A aptidão soma pontos por resultado, proporção de devoluções e uma parcela do saldo. As duas mais bem classificadas passam diretamente e as outras seis vagas vêm de seleção por torneio, cruzamento e mutação.

A função que avalia as três ações serve para calcular os alvos durante a coleta. Nas partidas, incluindo as contra pessoas, a ação vem da rede. O adversário de reflexo é usado só na avaliação, acompanha o X atual da bola e não gera exemplos de treino.

## 3 - Experimentos e resultados

Usei sementes de treino 42, 7 e 123. As taxas de aprendizagem/mutação foram, respectivamente, 0,08/0,06, 0,12/0,08 e 0,06/0,03. Primeiro testei recompensar apenas a ação tomada pelo progresso no posicionamento, com 18 gerações, 2 rodadas e partidas de 7 s. Depois testei alvos para as três ações e aptidão defensiva, com 20 gerações, 3 rodadas e partidas de 9 s. Como os orçamentos e mais de uma configuração mudaram, a comparação não isola o efeito de cada hiperparâmetro.

A triagem usou quatro sementes de partida (sorteadas com a semente 6001), com troca de lados. A rede da semente 7 na geração 20 venceu as oito partidas da triagem e sofreu zero pontos. As medições abaixo usaram outras 12 sementes de partida (sorteadas com 20261122), também com troca de lados. Taxa de devolução = rebatidas divididas por rebatidas mais pontos sofridos. Uma bola ainda em jogo no encerramento não entra nesse cálculo.

| Adversário e condições | Vitórias, empates e derrotas | Placar agregado | Rebatidas | Taxa de devolução |
| --- | --- | --- | ---: | ---: |
| Raquete parada, velocidade 4, 12 s | 24 / 0 / 0 | 46 a 0 | 48 | 100% |
| Rede anterior, velocidade 4, 12 s | 22 / 2 / 0 | 41 a 0 | 46 | 100% |
| Reflexo automático, velocidade 4, 12 s | 0 / 24 / 0 | 0 a 0 | 36 | 100% |
| Reflexo automático, velocidade 7, 18 s | 0 / 24 / 0 | 0 a 0 | 96 | 100% |
| Reflexo automático, velocidade 10, 18 s | 0 / 12 / 12 | 0 a 21 | 111 | 84,09% |

Em um teste adicional com 8 sementes (sorteio com 20261123), partidas de 90 s e velocidade inicial 4, a rede fez 185 rebatidas, sofreu zero pontos e empatou as 16 partidas contra o reflexo.

Na mesma amostra de validação, a rede anterior devolveu 0 bolas contra a raquete parada. Contra o reflexo em velocidade 7 devolveu 17 bolas, sofreu 104 pontos e perdeu as 24 partidas. O arquivo [resultados-treino-raquetes.json](resultados-treino-raquetes.json) reúne a triagem e os resultados das outras sementes e adversários.

No Edge, o nível Difícil carregou a Rede validada. Em partida de 12 s, a rede venceu por 2 a 0 contra a raquete humana deixada parada. Também confirmei treino no navegador, seleção da arena 3 e da rede do lado de cima, pausa, mudança de ritmo para Máximo, retomada e conclusão. O teste de interface e o adversário de reflexo não substituem uma série de partidas contra pessoas.

## 4 - Como reproduzir

Na pasta backend, com o motor Java compilado:

```powershell
.\.venv\Scripts\python.exe treinar_raquetes.py --semente 7 --geracoes 20 --rodadas 3 --duracao 9 --pontos 3 --velocidade 4 --incremento 0.45 --taxa 0.12 --mutacao 0.08 --ritmo 0 --saida reproduzida.json
.\.venv\Scripts\python.exe medir_raquetes.py modelo_validado.json --pares 12 --semente 20261122 --contra-reflexo --velocidade 7 --duracao 18
```

Para explicar na prova: **o motor simula a partida e fornece os estados, o backpropagation ajusta os pesos a partir da qualidade das ações nesses estados, e o algoritmo genético seleciona e combina as redes que jogaram melhor**. A melhora veio de fornecer feedback sobre o posicionamento e medir rebatidas, além de medir o placar.
