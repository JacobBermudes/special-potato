/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.backend;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.util.Log;

import org.amnezia.awg.backend.BackendException.Reason;
import org.amnezia.awg.backend.Tunnel.State;
import org.amnezia.awg.util.SharedLibraryLoader;
import org.amnezia.awg.config.Config;
import org.amnezia.awg.config.InetEndpoint;
import org.amnezia.awg.config.InetNetwork;
import org.amnezia.awg.config.Peer;
import org.amnezia.awg.crypto.Key;
import org.amnezia.awg.crypto.KeyFormatException;
import org.amnezia.awg.util.NonNullForAll;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import androidx.annotation.Nullable;
import androidx.collection.ArraySet;
import androidx.core.app.NotificationCompat;

import static org.amnezia.awg.GoBackend.*;

/**
 * Implementation of {@link Backend} that uses the amneziawg-go userspace implementation to provide
 * AmneziaWG tunnels.
 */
@NonNullForAll
public final class GoBackend implements Backend {
    private static final int DNS_RESOLUTION_RETRIES = 10;
    private static final String TAG = "SurfBoost/GoBackend";
    @Nullable private static AlwaysOnCallback alwaysOnCallback;
    private static GhettoCompletableFuture<VpnService> vpnService = new GhettoCompletableFuture<>();
    private final Context context;
    @Nullable private Config currentConfig;
    @Nullable private Tunnel currentTunnel;
    private int currentTunnelHandle = -1;
    @Nullable private Thread statusThread;
    @Nullable private StatusCallback statusCallback;

    /**
     * Public constructor for GoBackend.
     *
     * @param context An Android {@link Context}
     */
    public GoBackend(final Context context) {
        SharedLibraryLoader.loadSharedLibrary(context, "wg-go");
        this.context = context;
    }

    /**
     * Set a {@link AlwaysOnCallback} to be invoked when {@link VpnService} is started by the
     * system's Always-On VPN mode.
     *
     * @param cb Callback to be invoked
     */
    public static void setAlwaysOnCallback(final AlwaysOnCallback cb) {
        alwaysOnCallback = cb;
    }

    @Override
    public Set<String> getRunningTunnelNames() {
        if (currentTunnel != null) {
            final Set<String> runningTunnels = new ArraySet<>();
            runningTunnels.add(currentTunnel.getName());
            return runningTunnels;
        }
        return Collections.emptySet();
    }

    @Override
    public State getState(final Tunnel tunnel) {
        return currentTunnel == tunnel ? State.UP : State.DOWN;
    }

    @Override
    public Statistics getStatistics(final Tunnel tunnel) {
        final Statistics stats = new Statistics();
        if (tunnel != currentTunnel || currentTunnelHandle == -1)
            return stats;
        final String config = awgGetConfig(currentTunnelHandle);
        if (config == null)
            return stats;
        Key key = null;
        long rx = 0;
        long tx = 0;
        long latestHandshakeMSec = 0;
        for (final String line : config.split("\\n")) {
            if (line.startsWith("public_key=")) {
                if (key != null)
                    stats.add(key, rx, tx, latestHandshakeMSec);
                rx = 0;
                tx = 0;
                latestHandshakeMSec = 0;
                try {
                    key = Key.fromHex(line.substring(11));
                } catch (final KeyFormatException ignored) {
                    key = null;
                }
            } else if (line.startsWith("rx_bytes=")) {
                if (key == null)
                    continue;
                try {
                    rx = Long.parseLong(line.substring(9));
                } catch (final NumberFormatException ignored) {
                    rx = 0;
                }
            } else if (line.startsWith("tx_bytes=")) {
                if (key == null)
                    continue;
                try {
                    tx = Long.parseLong(line.substring(9));
                } catch (final NumberFormatException ignored) {
                    tx = 0;
                }
            } else if (line.startsWith("last_handshake_time_sec=")) {
                if (key == null)
                    continue;
                try {
                    latestHandshakeMSec += Long.parseLong(line.substring(24)) * 1000;
                } catch (final NumberFormatException ignored) {
                    latestHandshakeMSec = 0;
                }
            } else if (line.startsWith("last_handshake_time_nsec=")) {
                if (key == null)
                    continue;
                try {
                    latestHandshakeMSec += Long.parseLong(line.substring(25)) / 1000000;
                } catch (final NumberFormatException ignored) {
                    latestHandshakeMSec = 0;
                }
            }
        }
        if (key != null)
            stats.add(key, rx, tx, latestHandshakeMSec);
        return stats;
    }

    @Override
    public long getLastHandshake(final Tunnel tunnel) {
        if (tunnel != currentTunnel || currentTunnelHandle == -1)
            return -3;
        final String config = awgGetConfig(currentTunnelHandle);
        if (config == null) return -2;

        for (final String line : config.split("\\n")) {
            if (line.startsWith("last_handshake_time_sec=")) {
                try {
                    return Long.parseLong(line.substring(24));
                } catch (final NumberFormatException ignored) {
                    return -2;
                }
            }
        }
        return -1;
    }

    public void setStatusCallback(@Nullable final StatusCallback callback) {
        this.statusCallback = callback;
    }

    private void launchStatusJob() {
        stopStatusJob();
        statusThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                final long lastHandshake = getLastHandshake(currentTunnel);
                if (lastHandshake == -3L) break;
                if (lastHandshake == 0L) {
                    try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
                    continue;
                }
                if (lastHandshake > 0L) {
                    if (statusCallback != null) {
                        statusCallback.onStatusChanged(true);
                        VpnService.updateNotification(context, currentTunnel != null ? currentTunnel.getName() : "");
                    }
                    break;
                }
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
            }
        }, "StatusJob");
        statusThread.start();
    }

    private void stopStatusJob() {
        if (statusThread != null) {
            statusThread.interrupt();
            statusThread = null;
        }
    }

    @Override
    public String getVersion() {
        return awgVersion();
    }

    @Override
    public State setState(final Tunnel tunnel, State state, @Nullable final Config config) throws Exception {
        final State originalState = getState(tunnel);
        if (state == State.TOGGLE)
            state = originalState == State.UP ? State.DOWN : State.UP;
        if (state == originalState && tunnel == currentTunnel && config == currentConfig)
            return originalState;
        if (state == State.UP) {
            final Config originalConfig = currentConfig;
            final Tunnel originalTunnel = currentTunnel;
            if (currentTunnel != null)
                setStateInternal(currentTunnel, null, State.DOWN);
            try {
                setStateInternal(tunnel, config, state);
            } catch (final Exception e) {
                if (originalTunnel != null)
                    setStateInternal(originalTunnel, originalConfig, State.UP);
                throw e;
            }
        } else if (state == State.DOWN && tunnel == currentTunnel) {
            setStateInternal(tunnel, null, State.DOWN);
        }
        return getState(tunnel);
    }

    private void setStateInternal(final Tunnel tunnel, @Nullable final Config config, final State state)
            throws Exception {
        if (state == State.UP) {
            if (config == null) throw new BackendException(Reason.TUNNEL_MISSING_CONFIG);
            if (VpnService.prepare(context) != null) throw new BackendException(Reason.VPN_NOT_AUTHORIZED);

            final VpnService service;
            if (!vpnService.isDone()) {
                context.startService(new Intent(context, VpnService.class));
            }

            try {
                service = vpnService.get(2, TimeUnit.SECONDS);
            } catch (final TimeoutException e) {
                final Exception be = new BackendException(Reason.UNABLE_TO_START_VPN);
                be.initCause(e);
                throw be;
            }
            service.setOwner(this);

            if (currentTunnelHandle != -1) return;

            dnsRetry: for (int i = 0; i < DNS_RESOLUTION_RETRIES; ++i) {
                for (final Peer peer : config.getPeers()) {
                    final InetEndpoint ep = peer.getEndpoint().orElse(null);
                    if (ep == null) continue;
                    if (ep.getResolved().orElse(null) == null) {
                        if (i < DNS_RESOLUTION_RETRIES - 1) {
                            Thread.sleep(1000);
                            continue dnsRetry;
                        } else throw new BackendException(Reason.DNS_RESOLUTION_FAILURE, ep.getHost());
                    }
                }
                break;
            }

            final String goConfig = config.toAwgUserspaceString();
            final VpnService.Builder builder = service.getBuilder();
            builder.setSession(tunnel.getName());

            for (final String excludedApplication : config.getInterface().getExcludedApplications())
                builder.addDisallowedApplication(excludedApplication);
            for (final String includedApplication : config.getInterface().getIncludedApplications())
                builder.addAllowedApplication(includedApplication);
            for (final InetNetwork addr : config.getInterface().getAddresses())
                builder.addAddress(addr.getAddress(), addr.getMask());
            for (final InetAddress addr : config.getInterface().getDnsServers())
                builder.addDnsServer(addr.getHostAddress());
            for (final String dnsSearchDomain : config.getInterface().getDnsSearchDomains())
                builder.addSearchDomain(dnsSearchDomain);

            boolean sawDefaultRoute = false;
            for (final Peer peer : config.getPeers()) {
                for (final InetNetwork addr : peer.getAllowedIps()) {
                    if (addr.getMask() == 0) sawDefaultRoute = true;
                    builder.addRoute(addr.getAddress(), addr.getMask());
                }
            }

            if (!(sawDefaultRoute && config.getPeers().size() == 1)) {
                builder.allowFamily(OsConstants.AF_INET);
                builder.allowFamily(OsConstants.AF_INET6);
            }

            builder.setMtu(config.getInterface().getMtu().orElse(1280));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) service.setUnderlyingNetworks(null);

            builder.setBlocking(true);
            try (final ParcelFileDescriptor tun = builder.establish()) {
                if (tun == null) throw new BackendException(Reason.TUN_CREATION_ERROR);
                currentTunnelHandle = awgTurnOn(tunnel.getName(), tun.detachFd(), goConfig);
            }
            if (currentTunnelHandle < 0) throw new BackendException(Reason.GO_ACTIVATION_ERROR_CODE, currentTunnelHandle);

            currentTunnel = tunnel;
            currentConfig = config;

            service.protect(awgGetSocketV4(currentTunnelHandle));
            service.protect(awgGetSocketV6(currentTunnelHandle));

            launchStatusJob();
        } else {
            if (currentTunnelHandle == -1) return;
            stopStatusJob();
            int handleToClose = currentTunnelHandle;
            currentTunnel = null;
            currentTunnelHandle = -1;
            currentConfig = null;
            awgTurnOff(handleToClose);
            try {
                VpnService service = vpnService.get(0, TimeUnit.NANOSECONDS);
                service.stopForeground(true);
                service.stopSelf();
            } catch (final Exception ignored) { }
        }
        tunnel.onStateChange(state);
    }

    public interface AlwaysOnCallback {
        void alwaysOnTriggered();
    }

    private static final class GhettoCompletableFuture<V> {
        private final LinkedBlockingQueue<V> completion = new LinkedBlockingQueue<>(1);
        private final FutureTask<V> result = new FutureTask<>(completion::peek);

        public boolean complete(final V value) {
            final boolean offered = completion.offer(value);
            if (offered) result.run();
            return offered;
        }

        public V get() throws ExecutionException, InterruptedException { return result.get(); }
        public V get(final long timeout, final TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException { return result.get(timeout, unit); }
        public boolean isDone() { return !completion.isEmpty(); }
        public GhettoCompletableFuture<V> newIncompleteFuture() { return new GhettoCompletableFuture<>(); }
    }

    public static class VpnService extends android.net.VpnService {
        private static final String CHANNEL_ID = "SurfBoost_VPN";
        private static final int NOTIFICATION_ID = 12345;
        public static final String ACTION_DISCONNECT = "org.amnezia.awg.action.DISCONNECT";
        @Nullable private GoBackend owner;

        public Builder getBuilder() { return new Builder(); }

        @Override
        public void onCreate() {
            vpnService.complete(this);
            super.onCreate();
            createNotificationChannel();
        }

        private void createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "SurfBoost VPN Status", NotificationManager.IMPORTANCE_DEFAULT);
                channel.setShowBadge(false);
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) manager.createNotificationChannel(channel);
            }
        }

        public static void updateNotification(Context context, String tunnelName) {
            try {
                VpnService service = vpnService.get(0, TimeUnit.NANOSECONDS);

                Intent openAppIntent = new Intent(context, Class.forName("org.amnezia.awg.activity.MainActivity"));
                openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                PendingIntent openAppPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

                Intent disconnectIntent = new Intent(ACTION_DISCONNECT);
                disconnectIntent.setPackage(context.getPackageName());

                PendingIntent disconnectPendingIntent = PendingIntent.getBroadcast(context, 0, disconnectIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

                Notification notification = new NotificationCompat.Builder(service, CHANNEL_ID)
                        .setContentTitle("SurfBoost VPN")
                        .setContentText("Connected to " + tunnelName)
                        .setSmallIcon(context.getResources().getIdentifier("ic_notification", "drawable", context.getPackageName()))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setOngoing(true)
                        .setCategory(NotificationCompat.CATEGORY_SERVICE)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setContentIntent(openAppPendingIntent)
                        .addAction(0, "Disconnect", disconnectPendingIntent)
                        .build();

                service.startForeground(NOTIFICATION_ID, notification);
            } catch (Exception ignored) {}
        }

        @Override
        public void onDestroy() {
            if (owner != null) {
                final Tunnel tunnel = owner.currentTunnel;
                if (tunnel != null) {
                    if (owner.currentTunnelHandle != -1) awgTurnOff(owner.currentTunnelHandle);
                    owner.currentTunnel = null;
                    owner.currentTunnelHandle = -1;
                    owner.currentConfig = null;
                    tunnel.onStateChange(State.DOWN);
                }
            }
            vpnService = vpnService.newIncompleteFuture();
            super.onDestroy();
        }

        @Override
        public int onStartCommand(@Nullable final Intent intent, final int flags, final int startId) {
            vpnService.complete(this);
            if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
                if (owner != null) {
                    try { owner.setState(owner.currentTunnel, State.DOWN, null); } catch (Exception ignored) {}
                }
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }
            return super.onStartCommand(intent, flags, startId);
        }

        public void setOwner(final GoBackend owner) { this.owner = owner; }
    }
}
