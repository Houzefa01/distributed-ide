package com.distributed_ide.javafx_client.webrtc;

import com.distributed_ide.signaling.model.SignalingMessage;

/**
 * Abstraction d'une connexion WebRTC P2P : SDP (offer/answer) et ICE candidates.
 * Le flux média est établi P2P ; le backend ne gère que la signalisation.
 */
public interface WebRtcPeerConnection {

    /**
     * État de la connexion (pour extensions futures).
     */
    enum State { NEW, CONNECTING, CONNECTED, FAILED, CLOSED }

    /**
     * Crée une offre SDP (appelé par l'initiateur). Le résultat est fourni via onLocalDescription.
     */
    void createOffer();

    /**
     * Définit la description distante (SDP reçu de l'autre pair).
     */
    void setRemoteDescription(String sdp);

    /**
     * Crée une réponse SDP après avoir reçu une offre. Le résultat est fourni via onLocalDescription.
     */
    void createAnswer();

    /**
     * Ajoute un ICE candidate reçu via la signalisation.
     */
    void addIceCandidate(SignalingMessage.IceCandidate candidate);

    /**
     * Ferme la connexion et libère les ressources.
     */
    void close();

    /**
     * Listener pour les événements SDP/ICE à envoyer au pair via le signaling.
     */
    interface Listener {
        void onLocalDescription(String sdp);
        void onIceCandidate(SignalingMessage.IceCandidate candidate);
        void onStateChange(State state);
    }

    void setListener(Listener listener);
}
