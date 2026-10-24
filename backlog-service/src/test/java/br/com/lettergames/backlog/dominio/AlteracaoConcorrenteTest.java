package br.com.lettergames.backlog.dominio;

import br.com.lettergames.backlog.catalogo.CatalogoClient;
import br.com.lettergames.backlog.eventos.EventoOutbox;
import br.com.lettergames.backlog.eventos.EventoOutboxRepository;
import br.com.lettergames.backlog.eventos.OutboxRegistrador;
import br.com.lettergames.contratos.catalogo.JogoDTO;
import br.com.lettergames.contratos.evento.BacklogEvento;
import br.com.lettergames.contratos.evento.StatusBacklog;
import br.com.lettergames.contratos.evento.TipoEvento;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * PATCH/DELETE concorrentes no mesmo item. A 1ª transação é segurada dentro do lock (no registro do outbox)
 * até a 2ª estar comprovadamente esperando por ele: a ordem é determinística, sem depender de sleep.
 */
@SpringBootTest
@ActiveProfiles("test")
class AlteracaoConcorrenteTest {

    @Autowired
    private BacklogService service;

    @Autowired
    private ItemBacklogRepository itens;

    @Autowired
    private EventoOutboxRepository outboxRepository;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CatalogoClient catalogo;

    @MockitoSpyBean
    private OutboxRegistrador outbox;

    private final ExecutorService threads = Executors.newFixedThreadPool(2);

    @BeforeEach
    void preparar() {
        outboxRepository.deleteAll();
        itens.deleteAll();
        when(catalogo.buscarJogo(42L)).thenReturn(new JogoDTO(42L, "Hollow Knight", "PC", "Metroidvania", 27));
    }

    @AfterEach
    void encerrar() {
        threads.shutdownNow();
    }

    @Test
    void doisPatchConcorrentesGeramEventosEncadeados() throws Exception {
        long itemId = service.adicionar("joao", 42L).getId();
        outboxRepository.deleteAll();
        AtomicReference<Thread> segunda = new AtomicReference<>();
        CountDownLatch primeiraNoLock = segurarPrimeiraTransacaoAteASegundaEsperar(segunda);

        Future<ItemBacklog> patch1 = threads.submit(
                () -> service.alterarStatus("joao", itemId, StatusBacklog.JOGANDO));
        assertThat(primeiraNoLock.await(10, TimeUnit.SECONDS)).isTrue();
        Future<ItemBacklog> patch2 = threads.submit(() -> {
            segunda.set(Thread.currentThread());
            return service.alterarStatus("joao", itemId, StatusBacklog.ABANDONADO);
        });

        assertThat(patch1.get(20, TimeUnit.SECONDS).getStatus()).isEqualTo(StatusBacklog.JOGANDO);
        assertThat(patch2.get(20, TimeUnit.SECONDS).getStatus()).isEqualTo(StatusBacklog.ABANDONADO);

        List<BacklogEvento> eventos = eventosEmOrdem();
        assertThat(eventos).as("um evento por transição efetiva").hasSize(2);
        assertThat(eventos).extracting(BacklogEvento::tipo).containsOnly(TipoEvento.STATUS_ALTERADO);
        assertThat(eventos.get(0).statusAnterior()).isEqualTo(StatusBacklog.QUERO_JOGAR);
        assertThat(eventos.get(0).statusNovo()).isEqualTo(StatusBacklog.JOGANDO);
        assertThat(eventos.get(1).statusAnterior()).as("a 2ª transação viu o status já commitado")
                .isEqualTo(eventos.get(0).statusNovo());
        assertThat(eventos.get(1).statusNovo()).isEqualTo(StatusBacklog.ABANDONADO);
        assertThat(itens.findById(itemId).orElseThrow().getStatus()).isEqualTo(StatusBacklog.ABANDONADO);
    }

    @Test
    void patchQueEsperavaUmDeleteRecebeItemNaoEncontrado() throws Exception {
        long itemId = service.adicionar("joao", 42L).getId();
        outboxRepository.deleteAll();
        AtomicReference<Thread> segunda = new AtomicReference<>();
        CountDownLatch primeiraNoLock = segurarPrimeiraTransacaoAteASegundaEsperar(segunda);

        Future<?> delete = threads.submit(() -> service.remover("joao", itemId));
        assertThat(primeiraNoLock.await(10, TimeUnit.SECONDS)).isTrue();
        Future<ItemBacklog> patch = threads.submit(() -> {
            segunda.set(Thread.currentThread());
            return service.alterarStatus("joao", itemId, StatusBacklog.JOGANDO);
        });

        delete.get(20, TimeUnit.SECONDS);
        assertThatThrownBy(() -> patch.get(20, TimeUnit.SECONDS))
                .hasCauseInstanceOf(ItemNaoEncontradoException.class);
        assertThat(eventosEmOrdem()).extracting(BacklogEvento::tipo).containsExactly(TipoEvento.JOGO_REMOVIDO);
    }

    @Test
    void versaoBarraAlteracaoFeitaSemLockSobreLeituraDesatualizada() throws Exception {
        long itemId = service.adicionar("joao", 42L).getId();

        // Lê sem lock, outra transação altera e commita, e só então esta tenta gravar.
        assertThatThrownBy(() -> transacao.executeWithoutResult(status -> {
            ItemBacklog desatualizado = itens.findById(itemId).orElseThrow();
            try {
                threads.submit(() -> service.alterarStatus("joao", itemId, StatusBacklog.JOGANDO))
                        .get(20, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            desatualizado.alterarStatus(StatusBacklog.ABANDONADO);
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(itens.findById(itemId).orElseThrow().getStatus()).isEqualTo(StatusBacklog.JOGANDO);
    }

    /**
     * Na 1ª chamada ao outbox (já com o lock do item), avisa o teste e espera a thread da 2ª transação
     * ficar bloqueada no {@code SELECT ... FOR UPDATE}. Sem o lock, a 2ª nunca bloqueia e o teste falha.
     */
    private CountDownLatch segurarPrimeiraTransacaoAteASegundaEsperar(AtomicReference<Thread> segunda) {
        CountDownLatch primeiraNoLock = new CountDownLatch(1);
        AtomicBoolean primeiraChamada = new AtomicBoolean(true);
        OutboxRegistrador alvo = AopTestUtils.getUltimateTargetObject(outbox);
        doAnswer(invocacao -> {
            if (primeiraChamada.getAndSet(false)) {
                primeiraNoLock.countDown();
                esperarBloquear(segunda);
            }
            return invocacao.callRealMethod();
        }).when(alvo).registrar(any());
        return primeiraNoLock;
    }

    private static void esperarBloquear(AtomicReference<Thread> segunda) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < limite) {
            Thread thread = segunda.get();
            if (thread != null && (thread.getState() == Thread.State.WAITING
                    || thread.getState() == Thread.State.TIMED_WAITING)) {
                return;
            }
            Thread.sleep(10);
        }
    }

    private List<BacklogEvento> eventosEmOrdem() throws Exception {
        List<EventoOutbox> linhas = new ArrayList<>(outboxRepository.findAll());
        linhas.sort(Comparator.comparing(EventoOutbox::getCriadoEm));
        List<BacklogEvento> eventos = new ArrayList<>();
        for (EventoOutbox linha : linhas) {
            eventos.add(objectMapper.readValue(linha.getPayload(), BacklogEvento.class));
        }
        return eventos;
    }
}
