import desmoj.core.dist.BoolDistBernoulli;
import desmoj.core.dist.ContDistExponential;
import desmoj.core.dist.ContDistNormal;
import desmoj.core.simulator.Entity;
import desmoj.core.simulator.Event;
import desmoj.core.simulator.Experiment;
import desmoj.core.simulator.ExternalEvent;
import desmoj.core.simulator.Model;
import desmoj.core.simulator.Queue;
import desmoj.core.simulator.TimeInstant;
import desmoj.core.simulator.TimeSpan;

/**
 * Estação Charge+ - simulação de eventos discretos com DESMO-J.
 * Unidade de tempo: minutos. Duração de cada réplica: 840 min (14 h).
 *
 * Hipótese A: bateria crítica -> carregador rápido; não crítica -> convencional.
 * Os parâmetros pRapidoCritico / pRapidoNormal permitem outras hipóteses
 * (necessário para a pergunta b: com A puro as filas são homogêneas e a
 * prioridade não muda nada).
 */
public class ChargePlus {

    enum Modo { SEPARADAS_FIFO, SEPARADAS_PRIORIDADE, UNICA_FIFO, UNICA_PRIORIDADE }

    static final double DURACAO = 840.0;

    // ---------------------------------------------------------------- Entidade
    static class Veiculo extends Entity {
        final boolean critico;
        final boolean querRapido;   // fila escolhida (modo SEPARADAS)
        final double chegada;

        Veiculo(Model m, boolean critico, boolean querRapido, double chegada, boolean prioridade) {
            super(m, "Veiculo", false);
            this.critico = critico;
            this.querRapido = querRapido;
            this.chegada = chegada;
            setQueueingPriority(critico && prioridade ? 1 : 0); // maior prioridade fica à frente
        }
    }

    // ------------------------------------------------------------------ Eventos
    static class Chegada extends ExternalEvent {
        final Estacao m;
        Chegada(Estacao m) { super(m, "Chegada", false); this.m = m; }
        @Override public void eventRoutine() {
            m.veiculoChegou();
            schedule(new TimeSpan(m.intervalo.sample()));   // próxima chegada
        }
    }

    static class FimRecarga extends Event<Veiculo> {
        final Estacao m;
        FimRecarga(Estacao m) { super(m, "FimRecarga", false); this.m = m; }
        @Override public void eventRoutine(Veiculo v) { m.fimRecarga(v); }
    }

    // ------------------------------------------------------------------- Modelo
    static class Estacao extends Model {
        final int nR, nC;
        final Modo modo;
        final double pRapCrit, pRapNormal, fatorConv;

        ContDistExponential intervalo;
        ContDistNormal recR, recC;
        BoolDistBernoulli ehCritico, critVaiRapido, normalVaiRapido;
        Queue<Veiculo> filaR, filaC, filaU;

        int livresR, livresC;
        double ocupR, ocupC;                       // minutos-carregador ocupados
        double somaEspera, somaEsperaCrit, somaEsperaNorm;
        int atendidos, atendCrit, atendNorm;
        final boolean prioridade;

        Estacao(int nR, int nC, Modo modo, double pRapCrit, double pRapNormal, double fatorConv) {
            super(null, "Charge+", true, false);
            this.nR = nR; this.nC = nC; this.modo = modo;
            this.pRapCrit = pRapCrit; this.pRapNormal = pRapNormal; this.fatorConv = fatorConv;
            this.prioridade = (modo == Modo.SEPARADAS_PRIORIDADE || modo == Modo.UNICA_PRIORIDADE);
        }

        @Override public String description() {
            return "Estação de recarga Charge+ (filas, carregadores rápidos e convencionais).";
        }

        @Override public void init() {
            intervalo = new ContDistExponential(this, "Intervalo", 12.0, true, false);
            recR = new ContDistNormal(this, "RecargaRapida", 25.0, 6.0, true, false);
            recC = new ContDistNormal(this, "RecargaConv", 40.0 * fatorConv, 10.0 * fatorConv, true, false);
            ehCritico = new BoolDistBernoulli(this, "Critico", 0.35, true, false);
            critVaiRapido = new BoolDistBernoulli(this, "CritRapido", pRapCrit, true, false);
            normalVaiRapido = new BoolDistBernoulli(this, "NormalRapido", pRapNormal, true, false);
            filaR = new Queue<Veiculo>(this, "FilaRapida", true, false);
            filaC = new Queue<Veiculo>(this, "FilaConv", true, false);
            filaU = new Queue<Veiculo>(this, "FilaUnica", true, false);
            livresR = nR;
            livresC = nC;
        }

        @Override public void doInitialSchedules() {
            new Chegada(this).schedule(new TimeSpan(intervalo.sample()));
        }

        boolean unica() { return modo == Modo.UNICA_FIFO || modo == Modo.UNICA_PRIORIDADE; }

        double agora() { return presentTime().getTimeAsDouble(); }

        void veiculoChegou() {
            boolean crit = ehCritico.sample();
            boolean rapido = crit ? critVaiRapido.sample() : normalVaiRapido.sample();
            Veiculo v = new Veiculo(this, crit, rapido, agora(), prioridade);

            if (unica()) {
                if (livresR > 0)      iniciar(v, true);   // primeiro compatível livre (rápido tem preferência)
                else if (livresC > 0) iniciar(v, false);
                else                  filaU.insert(v);
            } else if (rapido) {
                if (livresR > 0) iniciar(v, true); else filaR.insert(v);
            } else {
                if (livresC > 0) iniciar(v, false); else filaC.insert(v);
            }
        }

        void iniciar(Veiculo v, boolean rapido) {
            double espera = agora() - v.chegada;
            somaEspera += espera; atendidos++;
            if (v.critico) { somaEsperaCrit += espera; atendCrit++; }
            else           { somaEsperaNorm += espera; atendNorm++; }

            double dur = Math.max(1.0, (rapido ? recR : recC).sample()); // trunca normal em 1 min
            if (rapido) { livresR--; ocupR += Math.min(dur, DURACAO - agora()); }
            else        { livresC--; ocupC += Math.min(dur, DURACAO - agora()); }

            // guarda o tipo usado na própria entidade de evento
            new FimRecarga(this).schedule(v, new TimeSpan(dur));
            tipoUsado.put(v, rapido);
        }

        final java.util.Map<Veiculo, Boolean> tipoUsado = new java.util.HashMap<Veiculo, Boolean>();

        void fimRecarga(Veiculo v) {
            boolean rapido = tipoUsado.remove(v);
            if (rapido) livresR++; else livresC++;

            Queue<Veiculo> q = unica() ? filaU : (rapido ? filaR : filaC);
            if (!q.isEmpty()) iniciar(q.removeFirst(), rapido);
        }
    }

    // --------------------------------------------------------------- Resultados
    static class Res {
        double espera, esperaCrit, esperaNorm;
        double filaMediaR, filaMediaC, filaMediaU;
        double filaMaxR, filaMaxC, filaMaxU;
        double utilR, utilC;
        double violou;          // 1 se alguma fila passou de 4 na réplica
        double atendidos;
    }

    static Res rodar(int nR, int nC, Modo modo, double pRapCrit, double pRapNormal,
                     double fatorConv, long seed) {
        Estacao m = new Estacao(nR, nC, modo, pRapCrit, pRapNormal, fatorConv);
        Experiment exp = new Experiment("ChargePlus");
        exp.setSeedGenerator(seed);
        exp.setShowProgressBar(false);
        m.connectToExperiment(exp);
        exp.stop(new TimeInstant(DURACAO));
        exp.start();

        Res r = new Res();
        r.espera = m.atendidos > 0 ? m.somaEspera / m.atendidos : 0;
        r.esperaCrit = m.atendCrit > 0 ? m.somaEsperaCrit / m.atendCrit : 0;
        r.esperaNorm = m.atendNorm > 0 ? m.somaEsperaNorm / m.atendNorm : 0;
        r.filaMediaR = m.filaR.averageLength();
        r.filaMediaC = m.filaC.averageLength();
        r.filaMediaU = m.filaU.averageLength();
        r.filaMaxR = m.filaR.maxLength();
        r.filaMaxC = m.filaC.maxLength();
        r.filaMaxU = m.filaU.maxLength();
        r.utilR = nR > 0 ? m.ocupR / (nR * DURACAO) : 0;
        r.utilC = nC > 0 ? m.ocupC / (nC * DURACAO) : 0;
        r.violou = (r.filaMaxR > 4 || r.filaMaxC > 4 || r.filaMaxU > 4) ? 1 : 0;
        r.atendidos = m.atendidos;
        exp.finish();
        return r;
    }

    /** Média de n réplicas independentes (sementes diferentes). */
    static Res replicar(int n, int nR, int nC, Modo modo, double pRC, double pRN, double fConv) {
        Res s = new Res();
        for (int i = 0; i < n; i++) {
            Res r = rodar(nR, nC, modo, pRC, pRN, fConv, 1000L + i);
            s.espera += r.espera / n;       s.esperaCrit += r.esperaCrit / n;
            s.esperaNorm += r.esperaNorm / n;
            s.filaMediaR += r.filaMediaR / n; s.filaMediaC += r.filaMediaC / n;
            s.filaMediaU += r.filaMediaU / n;
            s.filaMaxR += r.filaMaxR / n;   s.filaMaxC += r.filaMaxC / n;
            s.filaMaxU += r.filaMaxU / n;
            s.utilR += r.utilR / n;         s.utilC += r.utilC / n;
            s.violou += r.violou / n;       s.atendidos += r.atendidos / n;
        }
        return s;
    }

    // --------------------------------------------------------------------- main
    public static void main(String[] args) {
        int REP = 30;

        System.out.println("== (a) Base: hipótese A, filas separadas FIFO ==");
        System.out.println("nR nC | Wq(min) | maxFilaR maxFilaC | P(fila>4) | utilR utilC");
        for (int nR = 1; nR <= 3; nR++) {
            for (int nC = 2; nC <= 5; nC++) {
                Res r = replicar(REP, nR, nC, Modo.SEPARADAS_FIFO, 1.0, 0.0, 1.0);
                System.out.printf("%2d %2d | %7.2f | %8.2f %8.2f | %9.2f | %5.2f %5.2f%n",
                        nR, nC, r.espera, r.filaMaxR, r.filaMaxC, r.violou, r.utilR, r.utilC);
            }
        }

        // (b) filas heterogêneas: crítico 70% rápido, normal 30% rápido (hipótese de projeto)
        System.out.println("\n== (b) FIFO vs prioridade (filas mistas) ==");
        int nR = 2, nC = 4;
        for (Modo md : new Modo[] { Modo.SEPARADAS_FIFO, Modo.SEPARADAS_PRIORIDADE }) {
            Res r = replicar(REP, nR, nC, md, 0.7, 0.3, 1.0);
            System.out.printf("%-22s Wq=%.2f crit=%.2f norm=%.2f maxR=%.2f maxC=%.2f%n",
                    md, r.espera, r.esperaCrit, r.esperaNorm, r.filaMaxR, r.filaMaxC);
        }

        // (c) sistema inteligente: tempo convencional x 0,8
        System.out.println("\n== (c) Sistema inteligente (convencional -20%) ==");
        for (double f : new double[] { 1.0, 0.8 }) {
            Res r = replicar(REP, nR, nC, Modo.SEPARADAS_FIFO, 1.0, 0.0, f);
            System.out.printf("fator=%.1f Wq=%.2f maxC=%.2f utilC=%.2f atendidos=%.1f%n",
                    f, r.espera, r.filaMaxC, r.utilC, r.atendidos);
        }

        // (e) fila única
        System.out.println("\n== (e) Filas separadas vs fila única ==");
        for (Modo md : new Modo[] { Modo.SEPARADAS_FIFO, Modo.UNICA_FIFO, Modo.UNICA_PRIORIDADE }) {
            Res r = replicar(REP, nR, nC, md, 0.7, 0.3, 1.0);
            System.out.printf("%-22s Wq=%.2f crit=%.2f norm=%.2f utilR=%.2f utilC=%.2f%n",
                    md, r.espera, r.esperaCrit, r.esperaNorm, r.utilR, r.utilC);
        }
        // (d) tabela por hora: chamar replicar() alterando a média de chegada por faixa
    }
}
