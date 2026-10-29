package com.distributed_ide.javafx_client.webrtc;

import com.distributed_ide.signaling.model.SignalingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Implémentation stub de WebRtcPeerConnection : gère uniquement la signalisation SDP/ICE
 * sans pile média réelle. Permet de valider le flux multi-utilisateurs ; une implémentation
 * réelle (ex. webrtc-java) peut être branchée ensuite pour le flux média P2P.
 */
public class WebRtcPeerConnectionStub implements WebRtcPeerConnection {

    private static final Logger LOG = LoggerFactory.getLogger(WebRtcPeerConnectionStub.class);
    private static final String STUB_OFFER_PREFIX = "v=0\r\no=stub-offer ";
    private static final String STUB_ANSWER_PREFIX = "v=0\r\no=stub-answer ";
    private static final ExecutorService async = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "webrtc-stub-async");
        t.setDaemon(true);
        return t;
    });

    private volatile Listener listener;
    private volatile State state = State.NEW;
    private volatile boolean closed;

    @Override
    public void createOffer() {
        if (closed) return;
        setState(State.CONNECTING);
        async.execute(() -> {
            if (closed) return;
            String sdp = STUB_OFFER_PREFIX + System.currentTimeMillis() + "\r\n";
            notifyLocalDescription(sdp);
        });
    }

    @Override
    public void setRemoteDescription(String sdp) {
        if (closed) return;
        setState(State.CONNECTING);
        // Stub : on ne fait rien avec le SDP, juste stocké pour cohérence
    }

    @Override
    public void createAnswer() {
        if (closed) return;
        async.execute(() -> {
            if (closed) return;
            String sdp = STUB_ANSWER_PREFIX + System.currentTimeMillis() + "\r\n";
            notifyLocalDescription(sdp);
            setState(State.CONNECTED);
        });
    }

    @Override
    public void addIceCandidate(SignalingMessage.IceCandidate candidate) {
        if (closed) return;
        if (candidate != null) {
            LOG.debug("✅ ICE candidate reçu: {}", candidate.getCandidate());
        } else {
            LOG.warn("⚠️  ICE candidate null");
        }
        // Stub : on ne fait rien avec le candidate (pas de pile ICE réelle)
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        LOG.debug("🛑 Fermeture du stub de peer connection");
        setState(State.CLOSED);
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private void notifyLocalDescription(String sdp) {
        Listener l = listener;
        if (l != null) {
            runOnFx(() -> l.onLocalDescription(sdp));
        }
    }

    private void setState(State s) {
        state = s;
        Listener l = listener;
        if (l != null) {
            runOnFx(() -> l.onStateChange(s));
        }
    }

    private static void runOnFx(Runnable r) {
        try {
            if (javafx.application.Platform.isFxApplicationThread()) {
                r.run();
            } else {
                javafx.application.Platform.runLater(r);
            }
        } catch (Exception e) {
            r.run();
        }
    }

    /**
     * Arrête proprement le thread pool partagé.
     * À appeler lors du shutdown de l'application.
     */
    public static void shutdownExecutor() {
        LOG.info("🛑 Arrêt du thread pool WebRTC");
        async.shutdown();
        try {
            if (!async.awaitTermination(5, TimeUnit.SECONDS)) {
                async.shutdownNow();
                LOG.warn("⚠️  Force shutdown du thread pool WebRTC");
            }
        } catch (InterruptedException e) {
            async.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
