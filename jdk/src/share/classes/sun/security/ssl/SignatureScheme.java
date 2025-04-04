/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.security.ssl;

import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import sun.security.ssl.SupportedGroupsExtension.NamedGroup;
import sun.security.ssl.SupportedGroupsExtension.NamedGroupType;
import sun.security.ssl.SupportedGroupsExtension.SupportedGroups;
import sun.security.ssl.X509Authentication.X509Possession;
import sun.security.util.KeyUtil;
import sun.security.util.SignatureUtil;

enum SignatureScheme {
    // EdDSA algorithms
    ED25519                 (0x0807, "ed25519", "ed25519",
                                    "ed25519",
                                    ProtocolVersion.PROTOCOLS_OF_13),
    ED448                   (0x0808, "ed448", "ed448",
                                    "ed448",
                                    ProtocolVersion.PROTOCOLS_OF_13),

    // ECDSA algorithms
    ECDSA_SECP256R1_SHA256  (0x0403, "ecdsa_secp256r1_sha256",
                                    "SHA256withECDSA",
                                    "EC",
                                    NamedGroup.SECP256_R1,
                                    ProtocolVersion.PROTOCOLS_TO_13),
    ECDSA_SECP384R1_SHA384  (0x0503, "ecdsa_secp384r1_sha384",
                                    "SHA384withECDSA",
                                    "EC",
                                    NamedGroup.SECP384_R1,
                                    ProtocolVersion.PROTOCOLS_TO_13),
    ECDSA_SECP521R1_SHA512  (0x0603, "ecdsa_secp521r1_sha512",
                                    "SHA512withECDSA",
                                    "EC",
                                    NamedGroup.SECP521_R1,
                                    ProtocolVersion.PROTOCOLS_TO_13),


    // RSASSA-PKCS1-v1_5 algorithms
    RSA_PKCS1_SHA256        (0x0401, "rsa_pkcs1_sha256", "SHA256withRSA",
                                    "RSA", null, null, 511,
                                    ProtocolVersion.PROTOCOLS_TO_13,
                                    ProtocolVersion.PROTOCOLS_TO_12),
    RSA_PKCS1_SHA384        (0x0501, "rsa_pkcs1_sha384", "SHA384withRSA",
                                    "RSA", null, null, 768,
                                    ProtocolVersion.PROTOCOLS_TO_13,
                                    ProtocolVersion.PROTOCOLS_TO_12),
    RSA_PKCS1_SHA512        (0x0601, "rsa_pkcs1_sha512", "SHA512withRSA",
                                    "RSA", null, null, 768,
                                    ProtocolVersion.PROTOCOLS_TO_13,
                                    ProtocolVersion.PROTOCOLS_TO_12),

    // Legacy algorithms
    DSA_SHA256              (0x0402, "dsa_sha256", "SHA256withDSA",
                                    "DSA",
                                    ProtocolVersion.PROTOCOLS_TO_12),
    ECDSA_SHA224            (0x0303, "ecdsa_sha224", "SHA224withECDSA",
                                    "EC",
                                    ProtocolVersion.PROTOCOLS_TO_12),
    RSA_SHA224              (0x0301, "rsa_sha224", "SHA224withRSA",
                                    "RSA", 511,
                                    ProtocolVersion.PROTOCOLS_TO_12),
    DSA_SHA224              (0x0302, "dsa_sha224", "SHA224withDSA",
                                    "DSA",
                                    ProtocolVersion.PROTOCOLS_TO_12),
    ECDSA_SHA1              (0x0203, "ecdsa_sha1", "SHA1withECDSA",
                                    "EC",
                                    ProtocolVersion.PROTOCOLS_TO_13),
    RSA_PKCS1_SHA1          (0x0201, "rsa_pkcs1_sha1", "SHA1withRSA",
                                    "RSA", null, null, 511,
                                    ProtocolVersion.PROTOCOLS_TO_13,
                                    ProtocolVersion.PROTOCOLS_TO_12),
    DSA_SHA1                (0x0202, "dsa_sha1", "SHA1withDSA",
                                    "DSA",
                                    ProtocolVersion.PROTOCOLS_TO_12),
    RSA_MD5                 (0x0101, "rsa_md5", "MD5withRSA",
                                    "RSA", 511,
                                    ProtocolVersion.PROTOCOLS_TO_12);

    final int id;                       // hash + signature
    final String name;                  // literal name
    private final String algorithm;     // signature algorithm
    final String keyAlgorithm;          // signature key algorithm
    private final AlgorithmParameterSpec signAlgParameter;
    private final NamedGroup namedGroup;    // associated named group

    // The minimal required key size in bits.
    //
    // Only need to check RSA algorithm at present. RSA keys of 512 bits
    // have been shown to be practically breakable, it does not make much
    // sense to use the strong hash algorithm for keys whose key size less
    // than 512 bits.  So it is not necessary to calculate the minimal
    // required key size exactly for a hash algorithm.
    //
    // Note that some provider may use 511 bits for 512-bit strength RSA keys.
    final int minimalKeySize;
    final List<ProtocolVersion> supportedProtocols;

    // Some signature schemes are supported in different versions for handshake
    // messages and certificates. This field holds the supported protocols
    // for handshake messages.
    final List<ProtocolVersion> handshakeSupportedProtocols;
    final boolean isAvailable;

    private static final String[] hashAlgorithms = new String[] {
            "none",         "md5",      "sha1",     "sha224",
            "sha256",       "sha384",   "sha512"
        };

    private static final String[] signatureAlgorithms = new String[] {
            "anonymous",    "rsa",      "dsa",      "ecdsa",
        };

    // performance optimization
    private static final Set<CryptoPrimitive> SIGNATURE_PRIMITIVE_SET =
        Collections.unmodifiableSet(EnumSet.of(CryptoPrimitive.SIGNATURE));


    private SignatureScheme(int id, String name,
            String algorithm, String keyAlgorithm,
            ProtocolVersion[] supportedProtocols) {
        this(id, name, algorithm, keyAlgorithm, -1, supportedProtocols);
    }

    private SignatureScheme(int id, String name,
            String algorithm, String keyAlgorithm,
            int minimalKeySize,
            ProtocolVersion[] supportedProtocols) {
        this(id, name, algorithm, keyAlgorithm,
                null, minimalKeySize, supportedProtocols);
    }

    private SignatureScheme(int id, String name,
            String algorithm, String keyAlgorithm,
            AlgorithmParameterSpec signAlgParameter, int minimalKeySize,
            ProtocolVersion[] supportedProtocols) {
        this(id, name, algorithm, keyAlgorithm,
                signAlgParameter, null, minimalKeySize,
                supportedProtocols, supportedProtocols);
    }

    private SignatureScheme(int id, String name,
            String algorithm, String keyAlgorithm,
            NamedGroup namedGroup,
            ProtocolVersion[] supportedProtocols) {
        this(id, name, algorithm, keyAlgorithm,
                null, namedGroup, -1,
                supportedProtocols, supportedProtocols);
    }

    private SignatureScheme(int id, String name,
            String algorithm, String keyAlgorithm,
            AlgorithmParameterSpec signAlgParameter,
            NamedGroup namedGroup, int minimalKeySize,
            ProtocolVersion[] supportedProtocols,
            ProtocolVersion[] handshakeSupportedProtocols) {
        this.id = id;
        this.name = name;
        this.algorithm = algorithm;
        this.keyAlgorithm = keyAlgorithm;
        this.signAlgParameter = signAlgParameter;
        this.namedGroup = namedGroup;
        this.minimalKeySize = minimalKeySize;
        this.supportedProtocols = Arrays.asList(supportedProtocols);
        this.handshakeSupportedProtocols =
                Arrays.asList(handshakeSupportedProtocols);

        boolean mediator = true;
        // HACK CODE
        //
        // An EC provider, for example the SunEC provider, may support
        // AlgorithmParameters but not KeyPairGenerator or Signature.
        if ("EC".equals(keyAlgorithm)) {
            mediator = JsseJce.isEcAvailable();
        }

        // Check the specific algorithm and parameters.
        if (mediator) {
            try {
                JsseJce.getSignature(algorithm);
            } catch (Exception e) {
                mediator = false;
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.warning(
                        "Signature algorithm, " + algorithm +
                        ", is not supported by the underlying providers");
                }
            }
        }

        if (mediator && ((id >> 8) & 0xFF) == 0x03) {   // SHA224
            // There are some problems to use SHA224 on Windows.
            if (Security.getProvider("SunMSCAPI") != null) {
                mediator = false;
            }
        }

        this.isAvailable = mediator;
    }

    static SignatureScheme valueOf(int id) {
        for (SignatureScheme ss: SignatureScheme.values()) {
            if (ss.id == id) {
                return ss;
            }
        }

        return null;
    }

    static String nameOf(int id) {
        for (SignatureScheme ss: SignatureScheme.values()) {
            if (ss.id == id) {
                return ss.name;
            }
        }

        // Use TLS 1.2 style name for unknown signature scheme.
        int hashId = ((id >> 8) & 0xFF);
        int signId = (id & 0xFF);
        String hashName = (hashId >= hashAlgorithms.length) ?
            "UNDEFINED-HASH(" + hashId + ")" : hashAlgorithms[hashId];
        String signName = (signId >= signatureAlgorithms.length) ?
            "UNDEFINED-SIGNATURE(" + signId + ")" :
            signatureAlgorithms[signId];

        return signName + "_" + hashName;
    }

    // Note: the signatureSchemeName is not case-sensitive.
    static SignatureScheme nameOf(String signatureSchemeName) {
        for (SignatureScheme ss: SignatureScheme.values()) {
            if (ss.name.equalsIgnoreCase(signatureSchemeName)) {
                return ss;
            }
        }

        return null;
    }

    // Return the size of a SignatureScheme structure in TLS record
    static int sizeInRecord() {
        return 2;
    }

    // Get local supported algorithm collection complying to algorithm
    // constraints.
    static List<SignatureScheme> getSupportedAlgorithms(
            SSLConfiguration config,
            AlgorithmConstraints constraints,
            List<ProtocolVersion> activeProtocols) {
        List<SignatureScheme> supported = new LinkedList<>();
        for (SignatureScheme ss: SignatureScheme.values()) {
            if (!ss.isAvailable ||
                    (!config.signatureSchemes.isEmpty() &&
                        !config.signatureSchemes.contains(ss))) {
                if (SSLLogger.isOn &&
                        SSLLogger.isOn("ssl,handshake,verbose")) {
                    SSLLogger.finest(
                        "Ignore unsupported signature scheme: " + ss.name);
                }
                continue;
            }

            boolean isMatch = false;
            for (ProtocolVersion pv : activeProtocols) {
                if (ss.supportedProtocols.contains(pv)) {
                    isMatch = true;
                    break;
                }
            }

            if (isMatch) {
                if (constraints.permits(
                        SIGNATURE_PRIMITIVE_SET, ss.algorithm, null)) {
                    supported.add(ss);
                } else if (SSLLogger.isOn &&
                        SSLLogger.isOn("ssl,handshake,verbose")) {
                    SSLLogger.finest(
                        "Ignore disabled signature scheme: " + ss.name);
                }
            } else if (SSLLogger.isOn &&
                    SSLLogger.isOn("ssl,handshake,verbose")) {
                SSLLogger.finest(
                    "Ignore inactive signature scheme: " + ss.name);
            }
        }

        return supported;
    }

    static List<SignatureScheme> getSupportedAlgorithms(
            SSLConfiguration config,
            AlgorithmConstraints constraints,
            ProtocolVersion protocolVersion, int[] algorithmIds) {
        List<SignatureScheme> supported = new LinkedList<>();
        for (int ssid : algorithmIds) {
            SignatureScheme ss = SignatureScheme.valueOf(ssid);
            if (ss == null) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.warning(
                            "Unsupported signature scheme: " +
                            SignatureScheme.nameOf(ssid));
                }
            } else if (ss.isAvailable &&
                    ss.supportedProtocols.contains(protocolVersion) &&
                    (config.signatureSchemes.isEmpty() ||
                        config.signatureSchemes.contains(ss)) &&
                    constraints.permits(SIGNATURE_PRIMITIVE_SET,
                           ss.algorithm, null)) {
                supported.add(ss);
            } else {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.warning(
                            "Unsupported signature scheme: " + ss.name);
                }
            }
        }

        return supported;
    }

    static SignatureScheme getPreferableAlgorithm(
            List<SignatureScheme> schemes,
            SignatureScheme certScheme,
            ProtocolVersion version) {

        for (SignatureScheme ss : schemes) {
            if (ss.isAvailable &&
                    ss.handshakeSupportedProtocols.contains(version) &&
                    certScheme.keyAlgorithm.equalsIgnoreCase(ss.keyAlgorithm)) {

                return ss;
            }
        }

        return null;
    }

    static Map.Entry<SignatureScheme, Signature> getSignerOfPreferableAlgorithm(
            List<SignatureScheme> schemes,
            X509Possession x509Possession,
            ProtocolVersion version) {

        PrivateKey signingKey = x509Possession.popPrivateKey;
        String keyAlgorithm = signingKey.getAlgorithm();
        int keySize;
        // Only need to check RSA algorithm at present.
        if (keyAlgorithm.equalsIgnoreCase("RSA") ||
                keyAlgorithm.equalsIgnoreCase("RSASSA-PSS")) {
            keySize = KeyUtil.getKeySize(signingKey);
        } else {
            keySize = Integer.MAX_VALUE;
        }
        for (SignatureScheme ss : schemes) {
            if (ss.isAvailable && (keySize >= ss.minimalKeySize) &&
                ss.handshakeSupportedProtocols.contains(version) &&
                keyAlgorithm.equalsIgnoreCase(ss.keyAlgorithm)) {
                if (ss.namedGroup != null &&
                    ss.namedGroup.type == NamedGroupType.NAMED_GROUP_ECDHE) {
                    ECParameterSpec params =
                            x509Possession.getECParameterSpec();
                    if (params != null &&
                            ss.namedGroup == NamedGroup.valueOf(params)) {
                        Signature signer = ss.getSigner(signingKey);
                        if (signer != null) {
                            return new SimpleImmutableEntry<>(ss, signer);
                        }
                    }

                    if (SSLLogger.isOn &&
                            SSLLogger.isOn("ssl,handshake,verbose")) {
                        SSLLogger.finest(
                            "Ignore the signature algorithm (" + ss +
                            "), unsupported EC parameter spec: " + params);
                    }
                } else if ("EC".equals(ss.keyAlgorithm)) {
                    // Must be a legacy signature algorithm, which does not
                    // specify the associated named groups.  The connection
                    // cannot be established if the peer cannot recognize
                    // the named group used for the signature.  RFC 8446
                    // does not define countermeasures for the corner cases.
                    // In order to mitigate the impact, we choose to check
                    // against the local supported named groups.  The risk
                    // should be minimal as applications should not use
                    // unsupported named groups for its certificates.
                    ECParameterSpec params =
                            x509Possession.getECParameterSpec();
                    if (params != null) {
                        NamedGroup keyGroup = NamedGroup.valueOf(params);
                        if (keyGroup != null &&
                                SupportedGroups.isSupported(keyGroup)) {
                            Signature signer = ss.getSigner(signingKey);
                            if (signer != null) {
                                return new SimpleImmutableEntry<>(ss, signer);
                            }
                        }
                    }

                    if (SSLLogger.isOn &&
                            SSLLogger.isOn("ssl,handshake,verbose")) {
                        SSLLogger.finest(
                            "Ignore the legacy signature algorithm (" + ss +
                            "), unsupported EC parameter spec: " + params);
                    }
                } else {
                    Signature signer = ss.getSigner(signingKey);
                    if (signer != null) {
                        return new SimpleImmutableEntry<>(ss, signer);
                    }
                }
            }
        }

        return null;
    }

    static String[] getAlgorithmNames(Collection<SignatureScheme> schemes) {
        if (schemes != null) {
            ArrayList<String> names = new ArrayList<>(schemes.size());
            for (SignatureScheme scheme : schemes) {
                names.add(scheme.algorithm);
            }

            return names.toArray(new String[0]);
        }

        return new String[0];
    }

    // This method is used to get the signature instance of this signature
    // scheme for the specific public key.  Unlike getSigner(), the exception
    // is bubbled up.  If the public key does not support this signature
    // scheme, it normally means the TLS handshaking cannot continue and
    // the connection should be terminated.
    Signature getVerifier(PublicKey publicKey) throws NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException {
        if (!isAvailable) {
            return null;
        }

        Signature verifier = Signature.getInstance(algorithm);
        SignatureUtil.initVerifyWithParam(verifier, publicKey, signAlgParameter);

        return verifier;
    }

    // This method is also used to choose preferable signature scheme for the
    // specific private key.  If the private key does not support the signature
    // scheme, {@code null} is returned, and the caller may fail back to next
    // available signature scheme.
    private Signature getSigner(PrivateKey privateKey) {
        if (!isAvailable) {
            return null;
        }

        try {
            Signature signer = Signature.getInstance(algorithm);
            SignatureUtil.initSignWithParam(signer, privateKey,
                signAlgParameter,
                null);
            return signer;
        } catch (NoSuchAlgorithmException | InvalidKeyException |
                InvalidAlgorithmParameterException nsae) {
            if (SSLLogger.isOn &&
                    SSLLogger.isOn("ssl,handshake,verbose")) {
                SSLLogger.finest(
                    "Ignore unsupported signature algorithm (" +
                    this.name + ")", nsae);
            }
        }

        return null;
    }
}
