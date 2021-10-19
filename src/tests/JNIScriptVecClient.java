/*
* To change this template, choose Tools | Templates
* and open the template in the editor.
*/
package tests;

import java.io.Writer;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.awt.image.BufferedImage;
import java.io.StringWriter;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;

import ai.PassiveAI;
import ai.RandomBiasedAI;
import ai.RandomNoAttackAI;
import ai.core.AI;
import ai.jni.JNIAI;
import ai.rewardfunction.RewardFunctionInterface;
import ai.jni.JNIInterface;
import ai.jni.Response;
import ai.jni.Responses;
import gui.PhysicalGameStateJFrame;
import gui.PhysicalGameStatePanel;
import rts.GameState;
import rts.PhysicalGameState;
import rts.Player;
import rts.PlayerAction;
import rts.Trace;
import rts.TraceEntry;
import rts.UnitAction;
import rts.UnitActionAssignment;
import rts.units.Unit;
import rts.units.UnitTypeTable;
import tests.JNIGridnetClientSelfPlay;

/**
 *
 * @author santi
 * 
 *         Once you have the server running (for example, run
 *         "RunServerExample.java"), set the proper IP and port in the variable
 *         below, and run this file. One of the AIs (ai1) is run remotely using
 *         the server.
 * 
 *         Notice that as many AIs as needed can connect to the same server. For
 *         example, uncomment line 44 below and comment 45, to see two AIs using
 *         the same server.
 * 
 */
public class JNIScriptVecClient {
    public JNIScriptClient[] clients;
    public int maxSteps;
    public int[] envSteps; 
    public RewardFunctionInterface[] rfs;
    public UnitTypeTable utt;
    boolean partialObs = false;

    // storage
    int[][][][] masks;
    int[][][][] observation;
    double[][] reward;
    boolean[][] done;
    Response[] rs;
    Responses responses;

    double[] terminalReward1;
    boolean[] terminalRone1;
    double[] terminalReward2;
    boolean[] terminalRone2;

    public JNIScriptVecClient(int a_max_steps, RewardFunctionInterface[] a_rfs, String a_micrortsPath, String a_mapPath,
        AI[] a_ai1s, AI[] a_ai2s, UnitTypeTable a_utt, boolean partial_obs) throws Exception {
        maxSteps = a_max_steps;
        utt = a_utt;
        rfs = a_rfs;
        partialObs = partial_obs;

        // initialize clients
        clients = new JNIScriptClient[a_ai2s.length];
        for (int i = 0; i < clients.length; i++) {
            clients[i] = new JNIScriptClient(a_rfs, a_micrortsPath, a_mapPath, a_ai1s, a_ai2s[i], a_utt, partialObs);
        }

        Response r = new JNIScriptClient(a_rfs, a_micrortsPath, a_mapPath, a_ai1s, new PassiveAI(a_utt), a_utt, partialObs).reset(0);
        int s2 = r.observation.length;
        int s3 = r.observation[0].length;
        int s4 = r.observation[0][0].length;

        responses = new Responses(null, null, null);
        rs = new Response[a_ai2s.length];
        observation = new int[a_ai2s.length][s2][s3][s4];
        reward = new double[a_ai2s.length][rfs.length];
        done = new boolean[a_ai2s.length][rfs.length];
        envSteps = new int[a_ai2s.length];
        terminalReward1 = new double[rfs.length];
        terminalRone1 = new boolean[rfs.length];
    }

    public Responses reset(int[] players) throws Exception {
        for (int i = 0; i < clients.length; i++) {
            rs[i] = clients[i].reset(players[i]);
        }

        for (int i = 0; i < rs.length; i++) {
            observation[i] = rs[i].observation;
            reward[i] = rs[i].reward;
            done[i] = rs[i].done;
        }
        responses.set(observation, reward, done);
        return responses;
    }

    public Responses gameStep(int[] players, int[] aiIndex) throws Exception {
        for (int i = 0; i < clients.length; i++) {
            rs[i] = clients[i].gameStep(players[i], aiIndex[i]);
            envSteps[i] += 1;
            if (rs[i].done[0] || envSteps[i] >= maxSteps) {
                for (int j = 0; j < terminalReward1.length; j++) {
                    terminalReward1[j] = rs[i].reward[j];
                    terminalRone1[j] = rs[i].done[j];
                }
                clients[i].reset(players[i]);
                for (int j = 0; j < terminalReward1.length; j++) {
                    rs[i].reward[j] = terminalReward1[j];
                    rs[i].done[j] = terminalRone1[j];
                }
                rs[i].done[0] = true;
                envSteps[i] =0;
            }
        }
        for (int i = 0; i < rs.length; i++) {
            observation[i] = rs[i].observation;
            reward[i] = rs[i].reward;
            done[i] = rs[i].done;
        }
        responses.set(observation, reward, done);
        return responses;
    }

    public void close() throws Exception {
        if (clients != null) {
            for (JNIScriptClient client : clients) {
                client.close();
            }
        }
    }
}
