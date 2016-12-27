package com.datamountaineer.streamreactor.connect.mqtt.source

import java.io.FileReader
import java.security.{KeyStore, Security}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.{JcaPEMKeyConverter, JcePEMDecryptorProviderBuilder}
import org.bouncycastle.openssl.{PEMEncryptedKeyPair, PEMKeyPair, PEMParser}


object SSLSocketFactory extends StrictLogging {
  def apply(caCrtFile: String,
            crtFile: String,
            keyFile: String,
            password: String) = {
    try {

      /**
        * Add BouncyCastle as a Security Provider
        */
      Security.addProvider(new BouncyCastleProvider)
      val certificateConverter = new JcaX509CertificateConverter().setProvider("BC")
      /**
        * Load Certificate Authority (CA) certificate
        */
      var reader = new PEMParser(new FileReader(caCrtFile))
      val caCertHolder = reader.readObject.asInstanceOf[X509CertificateHolder]
      reader.close()
      val caCert = certificateConverter.getCertificate(caCertHolder)

      /**
        * Load client certificate
        */
      reader = new PEMParser(new FileReader(crtFile))
      val certHolder = reader.readObject.asInstanceOf[X509CertificateHolder]
      reader.close()
      val cert = certificateConverter.getCertificate(certHolder)

      /**
        * Load client private key
        */
      reader = new PEMParser(new FileReader(keyFile))
      val keyObject: Any = reader.readObject
      reader.close()

      val provider = new JcePEMDecryptorProviderBuilder().build(password.toCharArray)
      val keyConverter = new JcaPEMKeyConverter().setProvider("BC")
      var key = keyObject match {
        case pair: PEMEncryptedKeyPair => keyConverter.getKeyPair(pair.decryptKeyPair(provider))
        case _ => keyConverter.getKeyPair(keyObject.asInstanceOf[PEMKeyPair])
      }
      /**
        * CA certificate is used to authenticate server
        */
      val caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType)
      caKeyStore.load(null, null)
      caKeyStore.setCertificateEntry("ca-certificate", caCert)
      val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
      trustManagerFactory.init(caKeyStore)
      /**
        * Client key and certificates are sent to server so it can authenticate the client
        */
      val clientKeyStore = KeyStore.getInstance(KeyStore.getDefaultType)
      clientKeyStore.load(null, null)
      clientKeyStore.setCertificateEntry("certificate", cert)
      clientKeyStore.setKeyEntry("private-key", key.getPrivate, password.toCharArray, Array(cert))
      val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
      keyManagerFactory.init(clientKeyStore, password.toCharArray)
      /**
        * Create SSL socket factory
        */
      val context = SSLContext.getInstance("TLSv1.2")
      context.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, null)

      /**
        * Return the newly created socket factory object
        */
      context.getSocketFactory
    }
    catch {
      case e: Exception =>
        logger.warn(e.getMessage, e)
        null
    }
  }
}