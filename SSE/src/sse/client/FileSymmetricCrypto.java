package sse.client;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import sse.Token;

/**
 * 
 * @author Alex
 */
public class FileSymmetricCrypto {
    
    /**
     * Encrypts a file using a the file_password <Key,IV> pair
     * 
     * 
     * @param file File, a file as a byte[]. 
     * @param key Key used to encrypt the tokens
     * @param Base64_IV IV used to encrypt the tokens
     * @return a Base64 encoding of the file encrypted
     */
    public static String encrypt(byte[] file, SecretKey key, String Base64_IV) {
        
        IvParameterSpec IV = new IvParameterSpec(Base64.getDecoder().decode(Base64_IV));
        Cipher aesCipher = null;
        
        try {
            // Use AES in GCM mode. No padding is needed as
            // GCM is a streaming AEAD mode.
            aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
            aesCipher.init(Cipher.ENCRYPT_MODE, key, IV);
            
            // If the aesCipher object returned null w/o throwing any errors
            if(aesCipher == null)
                return null;

            // AES encrypted keyword
            final byte[] encrypted = aesCipher.doFinal(file);

            return Base64.getEncoder().encodeToString(encrypted);
        } 
        catch(NoSuchAlgorithmException nae)
        {
            nae.printStackTrace();
        }
        catch (InvalidKeyException ex) {
            Logger.getLogger(Tokenizer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidAlgorithmParameterException ex) {
            Logger.getLogger(Tokenizer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchPaddingException ex) {
            Logger.getLogger(Tokenizer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalBlockSizeException ex) {
            Logger.getLogger(Tokenizer.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return null;
    }
    
    /**
     * Encrypts a file using a the file_password <Key,IV> pair
     * 
     * @param cipher_text Encrypted File represented as the file text
     * @param key Key used to encrypt the tokens
     * @param Base64_IV IV used to encrypt the tokens
     * @return a Base64 encoding of the file encrypted
     */
    public static byte[] encrypt(String cipher_text, SecretKey key, String Base64_IV) {
        
        IvParameterSpec IV = new IvParameterSpec(Base64.getDecoder().decode(Base64_IV));
        Cipher aesCipher = null;
        
        try {
            // Use AES in GCM mode. No padding is needed as
            // GCM is a streaming AEAD mode.
            aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
            aesCipher.init(Cipher.DECRYPT_MODE, key, IV);
            
            byte[] decoded_cipher_text = Base64.getDecoder().decode(cipher_text);
            
            // Decrypt the cipher text
            byte[] decrypted = aesCipher.doFinal(decoded_cipher_text);

            return decrypted;
        } 
        catch(NoSuchAlgorithmException nae)
        {
            nae.printStackTrace();
        }
        catch (InvalidKeyException ex) {
            Logger.getLogger(Tokenizer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidAlgorithmParameterException ex) {
            Logger.getLogger(Tokenizer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchPaddingException ex) {
            Logger.getLogger(Tokenizer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalBlockSizeException ex) {
            Logger.getLogger(Tokenizer.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return null;
    }
    
}