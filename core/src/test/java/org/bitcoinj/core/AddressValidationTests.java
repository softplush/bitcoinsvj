package org.bitcoinj.core;

import org.bitcoinj.params.MainNetParams;
import org.junit.Test;
import static org.junit.Assert.*;

public class AddressValidationTests {

    @Test
    public void validateCashAddrs() {
        String cashAddrP2PKHValid = "bitcoincash:qzhr268ppgwtr36h20apc6ahzx6lsmlmcy40qejee0";
        assertTrue(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2PKHValid));

        String cashAddrP2PKHNoPrefixValid = "qzhr268ppgwtr36h20apc6ahzx6lsmlmcy40qejee0";
        assertTrue(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2PKHNoPrefixValid));

        String cashAddrP2SHValid = "bitcoincash:pqeft5ukkg9ew2fgt5axxnrq853nc5cuyvvpusv2nz";
        assertTrue(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2SHValid));

        String cashAddrP2SHNoPrefixValid = "pqeft5ukkg9ew2fgt5axxnrq853nc5cuyvvpusv2nz";
        assertTrue(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2SHNoPrefixValid));

        String cashAddrP2PKHInvalid = "bitcoincash:qzhr268ppgwtr36h2smlmcy40qejeec";
        assertFalse(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2PKHInvalid));

        String cashAddrP2PKHNoPrefixInvalid = "qzhr268ppgwtr36xclsmlmcy40qejeec";
        assertFalse(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2PKHNoPrefixInvalid));

        String cashAddrP2SHInvalid = "bitcoincash:pqef5axxnrq853nc5cuyvvpusv2nz";
        assertFalse(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2SHInvalid));

        String cashAddrP2SHNoPrefixInvalid = "pqeft5ukkg9ew2fgt5axxncuyvvpusv2nz";
        assertFalse(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2SHNoPrefixInvalid));
    }
}
