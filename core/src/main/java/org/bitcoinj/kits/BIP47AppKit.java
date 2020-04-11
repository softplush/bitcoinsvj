/* Copyright (c) 2017 Stash
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.bitcoinj.kits;


import com.github.kiulian.converter.AddressConverter;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.*;
import org.bitcoinj.core.bip47.BIP47Account;
import org.bitcoinj.core.bip47.BIP47Address;
import org.bitcoinj.core.bip47.BIP47Channel;
import org.bitcoinj.core.bip47.BIP47PaymentCode;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.crypto.BIP47SecretPoint;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.net.BlockingClientManager;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptException;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.utils.BIP47Util;
import org.bitcoinj.wallet.*;
import org.bitcoinj.wallet.bip47.listeners.BlockchainDownloadProgressTracker;
import org.bitcoinj.wallet.bip47.listeners.TransactionEventListener;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.utils.BIP47Util.getReceiveAddress;
import static org.bitcoinj.utils.BIP47Util.getSendAddress;

/**
 * Created by jimmy on 9/28/17.
 */

/**
 * <p>Runs a spv wallet and supports BIP 47 payments for coins. You will
 * need to instantiate one wallet per supported coin.</p>
 *
 * <p>It produces two files in a designated directory. The directory name is the coin name. and is created in workingDirectory: </p>
 * <ul>
 *     <il>The .spvchain (blockstore): maintains a maximum # of headers mapped to memory (5000)</il>
 *     <il>The .wallet: stores the wallet with txs, can be encrypted, storing keys</il>
 * </ul>
 *
 * <p>By using this kit, your wallet will import keys for bip 47 payment addresses, when a BIP 47
 * notification transaction is received.</p>
 */
public class BIP47AppKit extends AbstractIdleService {
    private static final String TAG = "BIP47AppKit";

    // fee parameters and network metadata
    private NetworkParameters params;
    // the blokstore is used by a blockchain as a memory data structure
    private volatile BlockChain vChain;
    private volatile BlockStore vStore;
    private volatile Wallet vWallet;
    // sync with the blockchain by using a peergroup
    private volatile PeerGroup vPeerGroup;
    protected PeerAddress[] peerAddresses;
    private DownloadProgressTracker progressTracker;
    private InputStream checkpoints;
    // the directory will have the spvchain and the wallet files
    private File directory;
    private volatile File vWalletFile;
    private String vWalletFileName;
    // Wether this wallet is restored from a BIP39 seed and will need to replay the complete blockchain
    // Will be null if it's not a restored wallet.
    private DeterministicSeed restoreFromSeed;
    private Runnable onReceiveRunnable;
    private boolean useTor = false;
    private String torProxyIp = "127.0.0.1";
    private String torProxyPort = "9050";

    // Support for BIP47-type accounts. Only one account is currently handled in this wallet.
    private List<BIP47Account> mAccounts = new ArrayList<BIP47Account>(1);

    // The progress tracker will callback the listener with a porcetage of the blockchain that it has downloaded, while downloading..
    private BlockchainDownloadProgressTracker mBlockchainDownloadProgressTracker;

    // This wallet allows one listener to be invoked when there are coins received and
    private TransactionEventListener mCoinsReceivedEventListener = null;
    // one listener when the transaction confidence changes
    private TransactionEventListener mTransactionConfidenceListener = null;

    private boolean mBlockchainDownloadStarted = false;

    // The payment channels indexed by payment codes.
    // A payment channel is created and saved if:
    //   - someone sends a notification transaction to this wallet's notifiction address
    //   - this wallet creates a notification transaction to a payment channel.
    //
    // It doesn't check if the notification transactions are mined before adding a payment code.
    // If you want to know a transaction's confidence, see #{@link Transaction.getConfidence()}
    private ConcurrentHashMap<String, BIP47Channel> bip47MetaData = new ConcurrentHashMap<String, BIP47Channel>();
    private static final Logger log = LoggerFactory.getLogger(BIP47AppKit.class);

    protected volatile Context context;

    public BIP47AppKit() {

    }

    public BIP47AppKit(NetworkParameters params, File file, String walletName) {
        this(params, KeyChainGroup.builder(params).fromRandom(Script.ScriptType.P2PKH).build(), file, walletName);
    }

    public BIP47AppKit(NetworkParameters params, DeterministicSeed seed, File file, String walletName) {
        this(params, KeyChainGroup.builder(params).fromSeed(seed, Script.ScriptType.P2PKH).build(), file, walletName);
    }

    private BIP47AppKit(Wallet wallet, File file, String walletName) {
        this.vWallet = wallet;
        this.params = this.vWallet.getParams();
        this.context = new Context(this.vWallet.getParams());
        this.directory = file;
        this.vWalletFileName = walletName;
        this.vWalletFile = new File(this.directory, walletName + ".wallet");
        this.completeSetupOfWallet();
    }

    private BIP47AppKit(NetworkParameters params, KeyChainGroup keyChainGroup, File file, String walletName) {
        this.setupWallet(params, keyChainGroup, file, walletName);
    }

    private void setupWallet(NetworkParameters params, KeyChainGroup keyChainGroup, File file, String walletName) {
        this.vWallet = new Wallet(params, keyChainGroup);
        this.params = params;
        this.context = new Context(params);
        this.directory = file;
        this.vWalletFileName = walletName;
        this.vWalletFile = new File(this.directory, walletName + ".wallet");
        this.completeSetupOfWallet();
    }

    private void completeSetupOfWallet() {
        this.setAccount();
        this.loadBip47MetaData();
        Address notificationAddress = this.mAccounts.get(0).getNotificationAddress();
        System.out.println("BIP47AppKit notification address: " + notificationAddress.toString());

        this.vWallet.allowSpendingUnconfirmedTransactions();
        this.vWallet.setAcceptRiskyTransactions(true);

        if (!this.vWallet.isAddressWatched(notificationAddress)) {
            this.vWallet.addWatchedAddress(notificationAddress);
        }

        String notifAsCashAddr = AddressConverter.toCashAddress(notificationAddress.toString());
    }

    public BIP47AppKit initialize(NetworkParameters params, File baseDir, String walletName, @Nullable DeterministicSeed seed) throws UnreadableWalletException {
        File tmpWalletFile = new File(baseDir, walletName + ".wallet");
        if (tmpWalletFile.exists()) {
            return loadFromFile(baseDir, walletName);
        } else {
            if (seed != null) {
                this.restoreFromSeed = seed;
                return new BIP47AppKit(params, seed, baseDir, walletName);
            } else {
                return new BIP47AppKit(params, baseDir, walletName);
            }
        }
    }

    private static BIP47AppKit loadFromFile(File baseDir, String walletName, @Nullable WalletExtension... walletExtensions) throws UnreadableWalletException {
        try {
            FileInputStream stream = null;
            try {
                stream = new FileInputStream(new File(baseDir, walletName + ".wallet"));
                Wallet wallet = Wallet.loadFromFileStream(stream, walletExtensions);
                return new BIP47AppKit(wallet, baseDir, walletName);
            } finally {
                if (stream != null) stream.close();
            }
        } catch (IOException e) {
            throw new UnreadableWalletException("Could not open file", e);
        }
    }

    public static boolean isWalletEncrypted(File baseDir, String walletName, @Nullable WalletExtension... walletExtensions) throws UnreadableWalletException {
        try {
            FileInputStream stream = null;
            try {
                stream = new FileInputStream(new File(baseDir, walletName + ".wallet"));
                Wallet wallet = Wallet.loadFromFileStream(stream, walletExtensions);
                return wallet.getKeyChainSeed().isEncrypted();
            } finally {
                if (stream != null) stream.close();
            }
        } catch (IOException e) {
            throw new UnreadableWalletException("Could not open file", e);
        }
    }

    public static Wallet getEncryptedWallet(File baseDir, String walletName, @Nullable WalletExtension... walletExtensions) throws UnreadableWalletException {
        try {
            FileInputStream stream = null;
            try {
                stream = new FileInputStream(new File(baseDir, walletName + ".wallet"));
                return Wallet.loadFromFileStream(stream, walletExtensions);
            } finally {
                if (stream != null) stream.close();
            }
        } catch (IOException e) {
            throw new UnreadableWalletException("Could not open file", e);
        }
    }

    // create peergroup for the blockchain
    private void derivePeerGroup() {
        Context.propagate(new Context(params));
        if (vPeerGroup == null)
            vPeerGroup = new PeerGroup(params, vChain);

        // connect to peers in the blockchain network
        vPeerGroup.addPeerDiscovery(new DnsDiscovery(params));

        // add the wallet to the peers so that every peer listener can find this wallet e.g. to invoke listeners
        vPeerGroup.addWallet(vWallet);
    }

    // BIP47-specific listener
    // When a new *notification* transaction is received:
    //  - new keys are generated and imported for incoming payments in the bip47 account/contact payment channel
    //  - the chain is rolled back 2 blocks so that payment transactions are not missed if in the same block as the notification transaction.
    //
    // When a new *payment* transaction is received:
    //  - a new key is generated and imported to the wallet
    private void addTransactionsListener(final Runnable runnable) {
        this.addOnReceiveTransactionListener(new TransactionEventListener() {
            @Override
            public void onTransactionReceived(BIP47AppKit bip47AppKit, Transaction transaction) {
                if (isNotificationTransaction(transaction)) {
                    System.out.println("Valid notification transaction received");
                    BIP47PaymentCode BIP47PaymentCode = getPaymentCodeInNotificationTransaction(transaction);
                    if (BIP47PaymentCode == null) {
                        System.err.println("Error decoding payment code in tx " + transaction);
                    } else {
                        System.out.println("Payment Code: " + BIP47PaymentCode);
                        boolean needsSaving = savePaymentCode(BIP47PaymentCode);
                        if (needsSaving) {
                            try {
                                rescanTxBlock(transaction);
                            } catch (BlockStoreException e) {
                                e.printStackTrace();
                            }
                            saveBip47MetaData();
                        }
                    }
                } else if (isToBIP47Address(transaction)) {
                    System.out.println("New BIP47 payment received to address: " + getAddressOfReceived(transaction));
                    boolean needsSaving = generateNewBip47IncomingAddress(getAddressOfReceived(transaction).toString());
                    if (needsSaving) {
                        saveBip47MetaData();
                    }
                    String paymentCode = getPaymentCodeForAddress(getAddressOfReceived(transaction).toString());
                    System.out.println("Received tx for Payment Code: " + paymentCode);
                } else {
                    Coin valueSentToMe = getValueSentToMe(transaction);
                    System.out.println("Received tx for " + valueSentToMe.toFriendlyString() + ":" + transaction);
                }

                if(runnable != null)
                    runnable.run();
            }

            @Override
            public void onTransactionConfidenceEvent(BIP47AppKit bip47AppKit, Transaction transaction) {
                return;
            }
        });
    }

    /**
     * <p>Create the account M/47'/0'/0' from the seed as a Bip47Account.</p>
     *
     * <p>After deriving, this wallet's payment code is available in @{link Bip47Wallet.getPaymentCode()}</p>
     */
    public void setAccount() {
        byte[] hd_seed = this.restoreFromSeed != null ?
                this.restoreFromSeed.getSeedBytes() :
                this.vWallet.getKeyChainSeed().getSeedBytes();


        DeterministicKey mKey = HDKeyDerivation.createMasterPrivateKey(hd_seed);
        DeterministicKey purposeKey = HDKeyDerivation.deriveChildKey(mKey, 47 | ChildNumber.HARDENED_BIT);
        DeterministicKey coinKey = HDKeyDerivation.deriveChildKey(purposeKey, ChildNumber.HARDENED_BIT);

        BIP47Account account = new BIP47Account(params, coinKey, 0);

        mAccounts.clear();
        mAccounts.add(account);
    }

    public List<Peer> getConnectedPeers() {
        return vPeerGroup.getConnectedPeers();
    }

    /**
     * <p>Reads the channels from .bip47 file. Return true if any payment code was loaded. </p>
     */
    public boolean loadBip47MetaData() {
        String jsonString = readBip47MetaDataFile();

        if (StringUtils.isEmpty(jsonString)) {
            return false;
        }

        System.out.println("loadBip47MetaData: " + jsonString);

        return importBip47MetaData(jsonString);
    }

    /**
     * <p>Reads the channels from .bip47 file. Return true if any payment code was loaded. </p>
     */
    public String readBip47MetaDataFile() {
        File file = new File(directory, this.vWalletFileName.concat(".bip47"));
        String jsonString;
        try {
            jsonString = FileUtils.readFileToString(file, Charset.defaultCharset());
        } catch (IOException e) {
            System.out.println("Creating BIP47 wallet file at " + file.getAbsolutePath() + "  ...");
            saveBip47MetaData();
            loadBip47MetaData();
            return null;
        }

        return jsonString;
    }

    /**
     * <p>Load channels from json. Return true if any payment code was loaded. </p>
     */
    public boolean importBip47MetaData(String jsonString) {
        System.out.println("loadBip47MetaData: " + jsonString);

        Gson gson = new Gson();
        Type collectionType = new TypeToken<Collection<BIP47Channel>>() {
        }.getType();
        try {
            List<BIP47Channel> BIP47ChannelList = gson.fromJson(jsonString, collectionType);
            if (BIP47ChannelList != null) {
                for (BIP47Channel BIP47Channel : BIP47ChannelList) {
                    bip47MetaData.put(BIP47Channel.getPaymentCode(), BIP47Channel);
                }
            }
        } catch (JsonSyntaxException e) {
            return true;
        }
        return false;
    }

    /**
     * <p>Persists the .bip47 file with the channels. </p>
     */
    public synchronized void saveBip47MetaData() {
        try {
            vWallet.saveToFile(vWalletFile);
        } catch (IOException io) {
            log.error("Failed to save wallet file", io);
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(bip47MetaData.values());

        System.out.println("saveBip47MetaData: " + json);

        File file = new File(directory, this.vWalletFileName.concat(".bip47"));

        try {
            FileUtils.writeStringToFile(file, json, Charset.defaultCharset(), false);
            System.out.println("saveBip47MetaData: saved");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * <p>A listener is added to be invoked when the wallet sees an incoming transaction. </p>
     */
    public void addOnReceiveTransactionListener(TransactionEventListener transactionEventListener) {
        if (this.mCoinsReceivedEventListener != null)
            vWallet.removeCoinsReceivedEventListener(mCoinsReceivedEventListener);

        transactionEventListener.setWallet(this);
        vWallet.addCoinsReceivedEventListener(transactionEventListener);

        mCoinsReceivedEventListener = transactionEventListener;
    }

    /**
     * <p>A listener is added to be invoked when the wallet receives blocks and builds confidence on a transaction </p>
     */
    public void addTransactionConfidenceEventListener(TransactionEventListener transactionEventListener) {
        if (this.mTransactionConfidenceListener != null)
            vWallet.removeTransactionConfidenceEventListener(mTransactionConfidenceListener);

        transactionEventListener.setWallet(this);
        vWallet.addTransactionConfidenceEventListener(transactionEventListener);

        mTransactionConfidenceListener = transactionEventListener;
    }

    public TransactionEventListener getCoinsReceivedEventListener() {
        return this.mCoinsReceivedEventListener;
    }

    /**
     * <p> Retrieve the relevant address (P2PKH or P2PSH) and compares it with the notification address of this wallet. </p>
     */
    public boolean isNotificationTransaction(Transaction tx) {
        Address address = getAddressOfReceived(tx);
        Address myNotificationAddress = mAccounts.get(0).getNotificationAddress();

        return address != null && address.toString().equals(myNotificationAddress.toString());
    }

    /**
     * <p> Retrieve the relevant address (P2PKH or P2PSH), return true if any key in this wallet translates to it. </p>
     */
    // TODO: return true if and only if it is a channel address.
    public boolean isToBIP47Address(Transaction transaction) {
        List<ECKey> keys = vWallet.getImportedKeys();
        for (ECKey key : keys) {
            Address address = key.toAddress(getParams());
            if (address == null) {
                continue;
            }
            Address addressOfReceived = getAddressOfReceived(transaction);
            if (addressOfReceived != null && address.toString().equals(addressOfReceived.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the address that received the transaction (P2PKH or P2PSH output)
     */
    public Address getAddressOfReceived(Transaction tx) {
        for (final TransactionOutput output : tx.getOutputs()) {
            try {
                if (output.isMineOrWatched(vWallet)) {
                    final Script script = output.getScriptPubKey();
                    return script.getToAddress(params, true);
                }
            } catch (final ScriptException x) {
                // swallow
            }
        }

        return null;
    }

    /* Find the address (in P2PKH or P2PSH output) that does not belong to this wallet. */
    public Address getAddressOfSent(Transaction tx) {
        for (final TransactionOutput output : tx.getOutputs()) {
            try {
                if (!output.isMineOrWatched(vWallet)) {
                    final Script script = output.getScriptPubKey();
                    return script.getToAddress(params, true);
                }
            } catch (final ScriptException x) {
                // swallow
            }
        }

        return null;
    }

    /**
     * Given a notification transaction, extracts a valid payment code
     */
    public BIP47PaymentCode getPaymentCodeInNotificationTransaction(Transaction tx) {
        byte[] privKeyBytes = mAccounts.get(0).getNotificationKey().getPrivKeyBytes();

        return BIP47Util.getPaymentCodeInNotificationTransaction(privKeyBytes, tx);
    }

    // <p> Receives a payment code and returns true iff there is already an incoming address generated for the channel</p>
    public boolean savePaymentCode(BIP47PaymentCode BIP47PaymentCode) {
        if (bip47MetaData.containsKey(BIP47PaymentCode.toString())) {
            BIP47Channel BIP47Channel = bip47MetaData.get(BIP47PaymentCode.toString());
            if (BIP47Channel.getIncomingAddresses().size() != 0) {
                return false;
            } else {
                try {
                    BIP47Channel.generateKeys(this);
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
        }

        BIP47Channel BIP47Channel = new BIP47Channel(BIP47PaymentCode.toString());

        try {
            BIP47Channel.generateKeys(this);
            bip47MetaData.put(BIP47PaymentCode.toString(), BIP47Channel);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public void rescanTxBlock(Transaction tx) throws BlockStoreException {
        try {
            if(tx.getConfidence().getAppearedAtChainHeight() - 2 > this.vChain.getBestChainHeight()) {
                System.out.println("Transaction is from block " + tx.getConfidence().getAppearedAtChainHeight() + " which is above our local chain height " + this.vChain.getBestChainHeight());
            } else {
                int blockHeight = tx.getConfidence().getAppearedAtChainHeight() - 2;
                this.vChain.rollbackBlockStore(blockHeight);
            }
        } catch (IllegalStateException e) {
            //fail silently, we dont need to rollback as it works when txs are in mempool
        }
    }

    public BIP47Account getAccount(int i) {
        return mAccounts.get(i);
    }

    public NetworkParameters getParams() {
        return this.params;
    }

    public Address getAddressOfKey(ECKey key) {
        return key.toAddress(getParams());
    }

    public void importKey(ECKey key) {
        vWallet.importKey(key);
    }

    /**
     * Return true if this is the first time the address is seen used
     */
    public boolean generateNewBip47IncomingAddress(String address) {
        for (BIP47Channel BIP47Channel : bip47MetaData.values()) {
            for (BIP47Address bip47Address : BIP47Channel.getIncomingAddresses()) {
                if (!bip47Address.getAddress().equals(address)) {
                    continue;
                }
                if (bip47Address.isSeen()) {
                    return false;
                }

                int nextIndex = BIP47Channel.getCurrentIncomingIndex() + 1;
                try {
                    ECKey key = getReceiveAddress(this, BIP47Channel.getPaymentCode(), nextIndex).getReceiveECKey();
                    vWallet.importKey(key);
                    Address newAddress = getAddressOfKey(key);
                    BIP47Channel.addNewIncomingAddress(newAddress.toString(), nextIndex);
                    bip47Address.setSeen(true);
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
            }
        }
        return false;
    }

    public BIP47Channel getBip47MetaForAddress(String address) {
        for (BIP47Channel BIP47Channel : bip47MetaData.values()) {
            for (BIP47Address bip47Address : BIP47Channel.getIncomingAddresses()) {
                if (bip47Address.getAddress().equals(address)) {
                    return BIP47Channel;
                }
            }
        }
        return null;
    }

    public String getPaymentCodeForAddress(String address) {
        for (BIP47Channel BIP47Channel : bip47MetaData.values()) {
            for (BIP47Address bip47Address : BIP47Channel.getIncomingAddresses()) {
                if (bip47Address.getAddress().equals(address)) {
                    return BIP47Channel.getPaymentCode();
                }
            }
        }
        return null;
    }

    public BIP47Channel getBip47MetaForOutgoingAddress(String address) {
        for (BIP47Channel BIP47Channel : bip47MetaData.values()) {
            for (String outgoingAddress : BIP47Channel.getOutgoingAddresses()) {
                if (outgoingAddress.equals(address)) {
                    return BIP47Channel;
                }
            }
        }
        return null;
    }

    public BIP47Channel getBip47MetaForPaymentCode(String paymentCode) {
        for (BIP47Channel BIP47Channel : bip47MetaData.values()) {
            if (BIP47Channel.getPaymentCode().equals(paymentCode)) {
                return BIP47Channel;
            }
        }
        return null;
    }

    public Coin getValueOfTransaction(Transaction transaction) {
        return transaction.getValue(vWallet);
    }

    public Coin getValueSentToMe(Transaction transaction) {
        return transaction.getValueSentToMe(vWallet);
    }

    public Coin getValueSentFromMe(Transaction transaction) {
        return transaction.getValueSentFromMe(vWallet);
    }

    public List<Transaction> getTransactions() {
        return vWallet.getTransactionsByTime();
    }

    public long getBalanceValue() {
        return vWallet.getBalance(org.bitcoinj.wallet.Wallet.BalanceType.ESTIMATED_SPENDABLE).getValue();
    }

    public Coin getBalance() {
        return vWallet.getBalance(org.bitcoinj.wallet.Wallet.BalanceType.ESTIMATED_SPENDABLE);
    }

    public boolean isDownloading() {
        return mBlockchainDownloadProgressTracker != null && mBlockchainDownloadProgressTracker.isDownloading();
    }

    public int getBlockchainProgress() {
        return mBlockchainDownloadProgressTracker != null ? mBlockchainDownloadProgressTracker.getProgress() : -1;
    }

    public boolean isTransactionEntirelySelf(Transaction tx) {
        for (final TransactionInput input : tx.getInputs()) {
            final TransactionOutput connectedOutput = input.getConnectedOutput();
            if (connectedOutput == null || !connectedOutput.isMine(vWallet))
                return false;
        }

        for (final TransactionOutput output : tx.getOutputs()) {
            if (!output.isMine(vWallet))
                return false;
        }

        return true;
    }

    public String getPaymentCode() {
        return getAccount(0).getStringPaymentCode();
    }

    public void resetBlockchainSync() {
        File chainFile = new File(directory, this.vWalletFileName + ".spvchain");
        if (chainFile.exists()) {
            System.out.println("deleteSpvFile: exits");
            chainFile.delete();
        }
    }

    public String getMnemonicCode() {
        return StringUtils.join(this.restoreFromSeed != null ?
                this.restoreFromSeed.getMnemonicCode() :
                vWallet.getKeyChainSeed().getMnemonicCode());
    }

    public Address getCurrentAddress() {
        return vWallet.currentReceiveAddress();
    }

    public LegacyAddress getAddressFromBase58(String addr) {
        return LegacyAddress.fromBase58(getParams(), addr);
    }

    private static Coin getDefaultFee(NetworkParameters params) {
        return Transaction.DEFAULT_TX_FEE;
    }

    public Transaction createSend(Address address, long amount) throws InsufficientMoneyException {
        SendRequest sendRequest = SendRequest.to(this.getParams(), address.toString(), Coin.valueOf(amount));

        sendRequest.feePerKb = getDefaultFee(getParams());

        vWallet.completeTx(sendRequest);
        return sendRequest.tx;
    }

    public SendRequest makeNotificationTransaction(String paymentCode, boolean allowUnconfirmedSpends) throws InsufficientMoneyException {
        BIP47Account toAccount = new BIP47Account(getParams(), paymentCode);
        Coin ntValue = getParams().getMinNonDustOutput();
        Address ntAddress = toAccount.getNotificationAddress();

        System.out.println("Balance: " + vWallet.getBalance());
        System.out.println("To notification address: " + ntAddress.toString());
        System.out.println("Value: " + ntValue.toFriendlyString());

        SendRequest sendRequest = SendRequest.to(this.getParams(), ntAddress.toString(), ntValue);

        if(allowUnconfirmedSpends)
            sendRequest.allowUnconfirmed();

        sendRequest.feePerKb = Coin.valueOf(1000L);
        sendRequest.memo = "notification_transaction";

        org.bitcoinj.utils.BIP47Util.FeeCalculation feeCalculation = BIP47Util.calculateFee(vWallet, sendRequest, ntValue, vWallet.calculateAllSpendCandidates());

        for (TransactionOutput output : feeCalculation.bestCoinSelection.gathered) {
            sendRequest.tx.addInput(output);
        }

        if (sendRequest.tx.getInputs().size() > 0) {
            TransactionInput txIn = sendRequest.tx.getInput(0);
            RedeemData redeemData = txIn.getConnectedRedeemData(vWallet);
            checkNotNull(redeemData, "StashTransaction exists in wallet that we cannot redeem: %s", txIn.getOutpoint().getHash());
            System.out.println("Keys: " + redeemData.keys.size());
            System.out.println("Private key 0?: " + redeemData.keys.get(0).hasPrivKey());
            byte[] privKey = redeemData.getFullKey().getPrivKeyBytes();
            System.out.println("Private key: " + Utils.HEX.encode(privKey));
            byte[] pubKey = toAccount.getNotificationKey().getPubKey();
            System.out.println("Public Key: " + Utils.HEX.encode(pubKey));
            byte[] outpoint = txIn.getOutpoint().bitcoinSerialize();

            byte[] mask = null;
            try {
                BIP47SecretPoint BIP47SecretPoint = new BIP47SecretPoint(privKey, pubKey);
                System.out.println("Secret Point: " + Utils.HEX.encode(BIP47SecretPoint.ECDHSecretAsBytes()));
                System.out.println("Outpoint: " + Utils.HEX.encode(outpoint));
                mask = BIP47PaymentCode.getMask(BIP47SecretPoint.ECDHSecretAsBytes(), outpoint);
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("My payment code: " + mAccounts.get(0).getPaymentCode().toString());
            System.out.println("Mask: " + Utils.HEX.encode(mask));
            byte[] op_return = BIP47PaymentCode.blind(mAccounts.get(0).getPaymentCode().getPayload(), mask);

            sendRequest.tx.addOutput(Coin.ZERO, ScriptBuilder.createOpReturnScript(op_return));
        }

        vWallet.completeTx(sendRequest);

        System.out.println("Completed SendRequest");

        sendRequest.tx.verify();

        return sendRequest;
    }

    public Transaction getSignedNotificationTransaction(SendRequest sendRequest, String paymentCode) {
        //BIP47Account toAccount = new BIP47Account(getParams(), paymentCode);

        // notification address pub key
        //BIP47Util.signTransaction(vWallet, sendRequest, toAccount.getNotificationKey().getPubKey(), mAccounts.get(0).getPaymentCode());

        vWallet.commitTx(sendRequest.tx);

        return sendRequest.tx;
    }

    public ListenableFuture<Transaction> broadcastTransaction(Transaction transactionToSend) {
        vWallet.commitTx(transactionToSend);
        return vPeerGroup.broadcastTransaction(transactionToSend).future();
    }

    public boolean putBip47Meta(String profileId, String name, @Nullable Transaction ntx) {
        if (bip47MetaData.containsKey(profileId)) {
            BIP47Channel BIP47Channel = bip47MetaData.get(profileId);
            if (ntx != null)
                BIP47Channel.setNtxHash(ntx.getHash());
            if (!name.equals(BIP47Channel.getLabel())) {
                BIP47Channel.setLabel(name);
                return true;
            }
        } else {
            bip47MetaData.put(profileId, new BIP47Channel(profileId, name));
            if (ntx != null)
                bip47MetaData.get(profileId).setNtxHash(ntx.getHash());
            return true;
        }
        return false;
    }

    /* Mark a channel's notification transaction as sent*/
    public void putPaymenCodeStatusSent(String paymentCode, Transaction ntx) {
        if (bip47MetaData.containsKey(paymentCode)) {
            BIP47Channel BIP47Channel = bip47MetaData.get(paymentCode);
            BIP47Channel.setNtxHash(ntx.getHash());
            BIP47Channel.setStatusSent();
        } else {
            putBip47Meta(paymentCode, paymentCode, ntx);
            putPaymenCodeStatusSent(paymentCode, ntx);
        }
    }

    /* Return the next address to send a payment to */
    public String getCurrentOutgoingAddress(BIP47Channel BIP47Channel) {
        try {
            ECKey key = getSendAddress(this, new BIP47PaymentCode(BIP47Channel.getPaymentCode()), BIP47Channel.getCurrentOutgoingIndex()).getSendECKey();
            return key.toAddress(getParams()).toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void commitTx(Transaction tx) {
        vWallet.commitTx(tx);
    }

    public org.bitcoinj.wallet.Wallet.SendResult sendCoins(SendRequest sendRequest) throws InsufficientMoneyException {
        return vWallet.sendCoins(sendRequest);
    }

    public File getDirectory() {
        return directory;
    }

    public File getvWalletFile() {
        return this.vWalletFile;
    }

    public PeerGroup getPeerGroup() {
        return this.vPeerGroup;
    }

    public org.bitcoinj.wallet.Wallet getvWallet() {
        return vWallet;
    }

    public List<String> getAddresses(int size) {
        List<DeterministicKey> deterministicKeys = vWallet.getActiveKeyChain().getLeafKeys();
        List<String> addresses = new ArrayList<String>(size);
        for (int i = 0; i < size; i++) {
            addresses.add(deterministicKeys.get(i).toAddress(getParams()).toString());
        }
        return addresses;
    }

    public void setUseTor(boolean status) {
        this.useTor = status;
    }

    public void setTorProxyIp(String ip) {
        this.torProxyIp = ip;
    }

    public void setTorProxyPort(String port) {
        this.torProxyPort = port;
    }

    public int getExternalAddressCount() {
        return vWallet.getActiveKeyChain().getIssuedReceiveKeys().size();
    }

    @Override
    protected void startUp() throws Exception {
        Context.propagate(context);
        this.startWallet();
    }

    @Override
    protected void shutDown() throws Exception {
        Context.propagate(context);
        this.vPeerGroup.stop();
        this.vWallet.saveToFile(this.vWalletFile);
        this.vStore.close();

        this.vPeerGroup = null;
        this.vWallet = null;
        this.vStore = null;
        this.vChain = null;
    }

    public void startWallet() throws BlockStoreException, IOException {
        File chainFile = new File(this.directory, this.vWalletFileName + ".spvchain");
        boolean chainFileExists = chainFile.exists();
        this.vStore = new SPVBlockStore(this.vWallet.getParams(), chainFile);
        if (!chainFileExists || this.restoreFromSeed != null) {
            if (this.checkpoints == null && !Utils.isAndroidRuntime()) {
                this.checkpoints = CheckpointManager.openStream(params);
            }

            if (this.checkpoints != null) {
                // Initialize the chain file with a checkpoint to speed up first-run sync.
                long time;
                if (this.restoreFromSeed != null) {
                    time = this.restoreFromSeed.getCreationTimeSeconds();
                    if (chainFileExists) {
                        this.vStore.close();
                        if (!chainFile.delete())
                            throw new IOException("Failed to delete chain file in preparation for restore.");
                        this.vStore = new SPVBlockStore(params, chainFile);
                    }
                } else {
                    time = this.vWallet.getEarliestKeyCreationTime();
                }
                if (time > 0) {
                    CheckpointManager.checkpoint(params, checkpoints, this.vStore, time);
                }
            } else if (chainFileExists) {
                this.vStore.close();
                if (!chainFile.delete())
                    throw new IOException("Failed to delete chain file in preparation for restore.");
                this.vStore = new SPVBlockStore(params, chainFile);
            }
        }

        this.vChain = new BlockChain(this.vWallet.getParams(), this.vStore);

        if(useTor) {
            System.setProperty("socksProxyHost", torProxyIp);
            System.setProperty("socksProxyPort", torProxyPort);
            this.vPeerGroup = new PeerGroup(this.vWallet.getParams(), this.vChain, new BlockingClientManager());
        } else {
            this.vPeerGroup = new PeerGroup(this.vWallet.getParams(), this.vChain);
        }

        if (peerAddresses != null) {
            for (PeerAddress addr : peerAddresses) this.vPeerGroup.addAddress(addr);
            this.vPeerGroup.setMaxConnections(peerAddresses.length);
            peerAddresses = null;
        } else if (!params.getId().equals(NetworkParameters.ID_REGTEST)) {
            this.vPeerGroup.addPeerDiscovery(new DnsDiscovery(this.vWallet.getParams()));
        }

        this.vChain.addWallet(this.vWallet);
        this.vPeerGroup.addWallet(this.vWallet);
        this.vWallet.autosaveToFile(new File(this.directory, this.vWalletFileName + ".wallet"), 5, TimeUnit.SECONDS, null);
        this.vWallet.saveToFile(new File(this.directory, this.vWalletFileName + ".wallet"));

        Futures.addCallback(vPeerGroup.startAsync(), new FutureCallback() {
            @Override
            public void onSuccess(@Nullable Object result) {
                final DownloadProgressTracker l = progressTracker == null ? new DownloadProgressTracker() : progressTracker;
                vPeerGroup.startBlockChainDownload(l);
            }

            @Override
            public void onFailure(Throwable t) {
                throw new RuntimeException(t);

            }
        }, MoreExecutors.directExecutor());

        this.addTransactionsListener(onReceiveRunnable);
    }

    public void setOnReceiveTxRunnable(Runnable runnable) {
        this.onReceiveRunnable = runnable;
    }

    public BIP47AppKit setCheckpoints(InputStream checkpoints) {
        if (this.checkpoints != null)
            Closeables.closeQuietly(checkpoints);
        this.checkpoints = checkNotNull(checkpoints);
        return this;
    }

    public void setPeerNodes(PeerAddress... addresses) {
        checkState(state() == State.NEW, "Cannot call after startup");
        this.peerAddresses = addresses;
    }

    public void setDownloadProgressTracker(DownloadProgressTracker progressTracker) {
        this.progressTracker = progressTracker;
    }
}
