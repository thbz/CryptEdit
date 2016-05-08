package org.thbz.CryptEdit;

/* Origine : code source de BouncyCastle / OpenPGP */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Date;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPDataValidationException;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPBEEncryptedData;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBEDataDecryptorFactoryBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBEKeyEncryptionMethodGenerator;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.util.io.Streams;

/**
 * A simple utility class.
 */
public class PBE
{
    /* A appeler (au moins) une fois avant d'utiliser les autres fonctions */
    static void init() {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void writeStringToLiteralData(OutputStream out,
						char literalDataType,
						String fileName,
						Date modificationTime,
						String data)
        throws IOException
    {
        PGPLiteralDataGenerator lData = new PGPLiteralDataGenerator();
	byte[] bytes = data.getBytes(Charset.forName("UTF-8"));
        OutputStream pOut = lData.open(out,
				       literalDataType,
				       fileName,
				       bytes.length,
				       modificationTime);
	// ThB, 20160805 : getBytes() utilise l'encodage par défaut de la plateforme : 
	// pour plus de portabilité, il paraît préférable d'imposer l'encodage
//	pOut.write(data.getBytes());
	pOut.write(bytes);
    }
    
    static byte[] compressString(String data,
				 String fileName,
				 Date fileModificationTime,
				 int algorithm)
	throws IOException
    {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        PGPCompressedDataGenerator comData =
	    new PGPCompressedDataGenerator(algorithm);
	OutputStream ostream = comData.open(bOut);
	writeStringToLiteralData(ostream,
				 PGPLiteralData.BINARY,
				 fileName,
				 fileModificationTime,
				 data);
        comData.close();
        return bOut.toByteArray();
    }

    /* fileName est le nom du fichier qui va être enregistré. Il est 
       prévu par la RFC 4880 pour un literal data packet. */
    static void encryptString(String data,
			      OutputStream out,
			      char[] passPhrase,
			      String fileName,
			      Date fileModificationTime,
			      boolean armor,
			      boolean withIntegrityCheck)
        throws IOException, NoSuchProviderException
    {
        if (armor) {
	    out = new ArmoredOutputStream(out);
        }

        try {
            byte[] compressedData =
		compressString(data,
			       fileName,
			       fileModificationTime,
			       CompressionAlgorithmTags.ZIP);

            PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator
		(new JcePGPDataEncryptorBuilder(PGPEncryptedData.CAST5)
		 .setWithIntegrityPacket(withIntegrityCheck)
		 .setSecureRandom(new SecureRandom())
		 .setProvider("BC"));

            encGen.addMethod(new JcePBEKeyEncryptionMethodGenerator(passPhrase)
			     .setProvider("BC"));

            OutputStream encOut = encGen.open(out, compressedData.length);
            encOut.write(compressedData);
            encOut.close();

            if(armor) {
                out.close();
            }
        }
        catch(PGPException e) {
	    System.err.println(e);
	    if(e.getUnderlyingException() != null) {
		e.getUnderlyingException().printStackTrace();
	    }
        }	
    }

    static InputStream decrypt(InputStream in,
			       char[] passPhrase)
        throws IOException, NoSuchProviderException, WrongPasswordException
    {
	try {
	    in = PGPUtil.getDecoderStream(in);
	    
	    JcaPGPObjectFactory pgpF = new JcaPGPObjectFactory(in);
	    PGPEncryptedDataList enc;
	    Object o = pgpF.nextObject();
	    
	    //
	    // the first object might be a PGP marker packet.
	    //
	    if(o instanceof PGPEncryptedDataList) {
		enc = (PGPEncryptedDataList)o;
	    }
	    else {
		enc = (PGPEncryptedDataList)pgpF.nextObject();
	    }
	    
	    PGPPBEEncryptedData pbe = (PGPPBEEncryptedData)enc.get(0);
	    
	    InputStream clear = pbe.getDataStream
		(new JcePBEDataDecryptorFactoryBuilder
		 (new JcaPGPDigestCalculatorProviderBuilder().setProvider("BC")
		  .build())
		 .setProvider("BC").build(passPhrase));
	    
	    JcaPGPObjectFactory pgpFact = new JcaPGPObjectFactory(clear);
	    
	    //
	    // if we're trying to read a file generated by someone other than us
	    // the data might not be compressed, so we check the return type from
	    // the factory and behave accordingly.
	    //
	    o = pgpFact.nextObject();
	    if (o instanceof PGPCompressedData) {
		PGPCompressedData cData = (PGPCompressedData)o;
		pgpFact = new JcaPGPObjectFactory(cData.getDataStream());
		o = pgpFact.nextObject();
	    }
	    
	    PGPLiteralData ld = (PGPLiteralData)o;
	    InputStream unc = ld.getInputStream();
	    
	    if (pbe.isIntegrityProtected()) {
		if (!pbe.verify()) {
		    System.err.println("message failed integrity check");
		}
		else {
		    // System.err.println("message integrity check passed");
		}
	    }
	    else {
		// System.err.println("no message integrity check");
	    }
	    
	    return unc;
	}
	catch(PGPDataValidationException exc) {
	    System.err.println("PGP data validation exception : "
			       + exc.toString());
	    throw new WrongPasswordException(exc);
	}
	catch(PGPException exc) {
	    System.err.println("Erreur PGP " + exc.toString());
	    return null;
	}
    }
}
