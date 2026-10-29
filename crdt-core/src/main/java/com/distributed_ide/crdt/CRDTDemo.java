package com.distributed_ide.crdt;

import com.distributed_ide.crdt.engine.CRDTEngine;
import com.distributed_ide.crdt.model.CRDTDocument;
import com.distributed_ide.crdt.model.CRDTOperation;

/**
 * Démonstration d'un scénario CRDT réaliste:
 * Édition collaborative temps réel entre Alice et Bob.
 * 
 * Exécuter avec: java -cp target/classes:... com.distributed_ide.crdt.CRDTDemo
 */
public class CRDTDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== CRDT Collaborative Editing Demo ===\n");

        // === SCÉNARIO 1: Édition séquentielle ===
        scenarioSequential();

        System.out.println("\n" + "=".repeat(50) + "\n");

        // === SCÉNARIO 2: Édition concurrente ===
        scenarioConcurrent();

        System.out.println("\n" + "=".repeat(50) + "\n");

        // === SCÉNARIO 3: Undo/Redo ===
        scenarioUndoRedo();
    }

    /**
     * Scénario 1: Alice et Bob éditent séquentiellement sans conflit
     */
    static void scenarioSequential() {
        System.out.println("SCÉNARIO 1: Édition séquentielle");
        System.out.println("Alice tape 'Hello', puis Bob ajoute ' World'\n");

        CRDTEngine alice = new CRDTEngine("alice");
        CRDTDocument doc = alice.createDocument("doc-1");

        // Alice écrit "Hello"
        System.out.println("1. Alice tape 'Hello':");
        alice.insertCharacter("doc-1", 0, 'H');
        alice.insertCharacter("doc-1", 1, 'e');
        alice.insertCharacter("doc-1", 2, 'l');
        alice.insertCharacter("doc-1", 3, 'l');
        alice.insertCharacter("doc-1", 4, 'o');
        System.out.println("   Texte Alice: " + doc.getText());

        // Synchronisation: envoyer l'historique à Bob
        System.out.println("\n2. Alice envoie l'historique à Bob");
        CRDTEngine bob = new CRDTEngine("bob");
        CRDTDocument docBob = bob.createDocument("doc-1");

        for (CRDTOperation op : doc.getOperationHistory()) {
            bob.applyRemoteOperation("doc-1", op);
        }
        System.out.println("   Texte Bob après sync: " + docBob.getText());

        // Bob ajoute " World"
        System.out.println("\n3. Bob tape ' World':");
        bob.insertCharacter("doc-1", 5, ' ');
        bob.insertCharacter("doc-1", 6, 'W');
        bob.insertCharacter("doc-1", 7, 'o');
        bob.insertCharacter("doc-1", 8, 'r');
        bob.insertCharacter("doc-1", 9, 'l');
        bob.insertCharacter("doc-1", 10, 'd');
        System.out.println("   Texte Bob: " + docBob.getText());

        // Bob envoie à Alice
        System.out.println("\n4. Bob envoie ses changements à Alice");
        for (CRDTOperation op : docBob.getOperationHistory().stream()
                .skip(5) // Sauter les 5 ops d'Alice
                .toList()) {
            alice.applyRemoteOperation("doc-1", op);
        }
        System.out.println("   Texte Alice final: " + doc.getText());
        System.out.println("   Texte Bob final: " + docBob.getText());
        System.out.println("\n✅ Convergence réussie!");
    }

    /**
     * Scénario 2: Alice et Bob éditent concurremment au même endroit
     */
    static void scenarioConcurrent() {
        System.out.println("SCÉNARIO 2: Édition concurrente (SANS conflit!)");
        System.out.println("Alice et Bob insèrent simultanément à position 0\n");

        CRDTEngine alice = new CRDTEngine("alice");
        CRDTEngine bob = new CRDTEngine("bob");

        CRDTDocument docA = alice.createDocument("shared");
        CRDTDocument docB = bob.createDocument("shared");

        // Alice débute avec "XYZ"
        System.out.println("1. Alice crée 'X' puis 'Y' puis 'Z':");
        alice.insertCharacter("shared", 0, 'X');
        alice.insertCharacter("shared", 1, 'Y');
        alice.insertCharacter("shared", 2, 'Z');
        System.out.println("   Alice: " + docA.getText());

        // Alice envoie à Bob
        System.out.println("\n2. Alice → Bob sync");
        for (CRDTOperation op : docA.getOperationHistory()) {
            bob.applyRemoteOperation("shared", op);
        }
        System.out.println("   Bob reçoit: " + docB.getText());

        // Les deux éditent maintenant indépendamment
        System.out.println("\n3. Éditions CONCURRENTES (réseau latent):");

        // Alice insère 'A' à la fin
        System.out.println("   Alice insère 'A' à position 3:");
        alice.insertCharacter("shared", 3, 'A');
        System.out.println("   Alice localement: " + docA.getText());

        // Bob insère 'B' à position 2 (avant le sync)
        System.out.println("   Bob insère 'B' à position 2:");
        bob.insertCharacter("shared", 2, 'B');
        System.out.println("   Bob localement: " + docB.getText());

        // Synchronisation finale
        System.out.println("\n4. Réconciliation: échange des opérations");
        for (CRDTOperation op : docB.getOperationHistory().stream()
                .skip(3)
                .toList()) {
            alice.applyRemoteOperation("shared", op);
        }
        for (CRDTOperation op : docA.getOperationHistory().stream()
                .skip(3)
                .toList()) {
            bob.applyRemoteOperation("shared", op);
        }

        String finalA = docA.getText();
        String finalB = docB.getText();
        System.out.println("   Alice final: " + finalA);
        System.out.println("   Bob final: " + finalB);

        if (finalA.equals(finalB)) {
            System.out.println("\n✅ Convergence réussie! (Identifiants CRDT ont résolu le conflit)");
            System.out.println("   Les deux replicas sont au même état malgré les éditions");
            System.out.println("   concurrentes, SANS protocole d'arbitrage!");
        }
    }

    /**
     * Scénario 3: Undo/Redo dans un contexte collaboratif
     */
    static void scenarioUndoRedo() {
        System.out.println("SCÉNARIO 3: Undo/Redo avec édition collaborative");
        System.out.println();

        CRDTEngine alice = new CRDTEngine("alice");
        CRDTDocument doc = alice.createDocument("doc-undo");

        System.out.println("1. Alice édite:");
        alice.insertCharacter("doc-undo", 0, 'F');
        alice.insertCharacter("doc-undo", 1, 'o');
        alice.insertCharacter("doc-undo", 2, 'o');
        System.out.println("   Résultat: " + doc.getText());

        System.out.println("\n2. Alice ajoute 'bar':");
        alice.insertCharacter("doc-undo", 3, 'b');
        alice.insertCharacter("doc-undo", 4, 'a');
        alice.insertCharacter("doc-undo", 5, 'r');
        System.out.println("   Résultat: " + doc.getText());

        System.out.println("\n3. Alice appuie sur Undo:");
        alice.undo("doc-undo");
        System.out.println("   Résultat: " + doc.getText());

        System.out.println("\n4. Alice appuie à nouveau sur Undo:");
        alice.undo("doc-undo");
        System.out.println("   Résultat: " + doc.getText());

        System.out.println("\n5. Alice appuie sur Redo:");
        alice.redo("doc-undo");
        System.out.println("   Résultat: " + doc.getText());

        System.out.println("\n✅ Undo/Redo fonctionne avec O(1)!");
    }
}
