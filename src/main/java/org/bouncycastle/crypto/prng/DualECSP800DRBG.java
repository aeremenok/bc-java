package org.bouncycastle.crypto.prng;

import java.math.BigInteger;

import org.bouncycastle.asn1.nist.NISTNamedCurves;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECFieldElement;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;

public class DualECSP800DRBG
    implements SP80090DRBG
{
    /*
     * Default P, Q values for each curve
     */
    private static final BigInteger p256_Px = new BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16);
    private static final BigInteger p256_Py = new BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16);
    private static final BigInteger p256_Qx = new BigInteger("c97445f45cdef9f0d3e05e1e585fc297235b82b5be8ff3efca67c59852018192", 16);
    private static final BigInteger p256_Qy = new BigInteger("b28ef557ba31dfcbdd21ac46e2a91e3c304f44cb87058ada2cb815151e610046", 16);

    private static final BigInteger p384_Px = new BigInteger("aa87ca22be8b05378eb1c71ef320ad746e1d3b628ba79b9859f741e082542a385502f25dbf55296c3a545e3872760ab7", 16);
    private static final BigInteger p384_Py = new BigInteger("3617de4a96262c6f5d9e98bf9292dc29f8f41dbd289a147ce9da3113b5f0b8c00a60b1ce1d7e819d7a431d7c90ea0e5f", 16);
    private static final BigInteger p384_Qx = new BigInteger("8e722de3125bddb05580164bfe20b8b432216a62926c57502ceede31c47816edd1e89769124179d0b695106428815065", 16);
    private static final BigInteger p384_Qy = new BigInteger("023b1660dd701d0839fd45eec36f9ee7b32e13b315dc02610aa1b636e346df671f790f84c5e09b05674dbb7e45c803dd", 16);

    private static final BigInteger p521_Px = new BigInteger("c6858e06b70404e9cd9e3ecb662395b4429c648139053fb521f828af606b4d3dbaa14b5e77efe75928fe1dc127a2ffa8de3348b3c1856a429bf97e7e31c2e5bd66", 16);
    private static final BigInteger p521_Py = new BigInteger("11839296a789a3bc0045c8a5fb42c7d1bd998f54449579b446817afbd17273e662c97ee72995ef42640c550b9013fad0761353c7086a272c24088be94769fd16650", 16);
    private static final BigInteger p521_Qx = new BigInteger("1b9fa3e518d683c6b65763694ac8efbaec6fab44f2276171a42726507dd08add4c3b3f4c1ebc5b1222ddba077f722943b24c3edfa0f85fe24d0c8c01591f0be6f63", 16);
    private static final BigInteger p521_Qy = new BigInteger("1f3bdba585295d9a1110d1df1f9430ef8442c5018976ff3437ef91b81dc0b8132c8d5c39c32d0e004a3092b7d327c0e7a4d26d2c7b69b58f9066652911e457779de", 16);

    private final static byte[]     ONE = { 0x01 };
    private final static int        RESEED_MAX = 100000;

    private Digest                 _digest;
    private int                    _reseedCounter;
    private EntropySource          _entropySource;
    private int                    _securityStrength;
    private int                    _seedlen;
    private int                    _outlen;
    private ECCurve.Fp             _curve;
    private ECPoint                _P;
    private ECPoint                _Q;
    private byte[]                 _s;

    public DualECSP800DRBG(Digest digest, EntropySource entropySource, byte[] nonce,
                           byte[] personalisationString, int securityStrength)
    {
        if (securityStrength > digest.getDigestSize() * 8) // TODO: this may, or may not be correct, but it's good enough for now
        {
            throw new IllegalStateException(
                    "Security strength is not supported by the derivation function");
        }

        // TODO: validate digest choice
        _digest = digest;
        _entropySource = entropySource;
        _securityStrength = securityStrength;

        // TODO: validate entropy length
        byte[] entropy = entropySource.getEntropy();

        byte[] seedMaterial = new byte[entropy.length + nonce.length + personalisationString.length];

        System.arraycopy(entropy, 0, seedMaterial, 0, entropy.length);
        System.arraycopy(nonce, 0, seedMaterial, entropy.length, nonce.length);
        System.arraycopy(personalisationString, 0, seedMaterial, entropy.length + nonce.length, personalisationString.length);

        if (securityStrength <= 128)
        {
            _seedlen = 256;
            _outlen = 240 / 8;
            _curve = (ECCurve.Fp)NISTNamedCurves.getByName("P-256").getCurve();
            _P = new ECPoint.Fp(_curve, new ECFieldElement.Fp(_curve.getQ(), p256_Px), new ECFieldElement.Fp(_curve.getQ(), p256_Py));
            _Q = new ECPoint.Fp(_curve, new ECFieldElement.Fp(_curve.getQ(), p256_Qx), new ECFieldElement.Fp(_curve.getQ(), p256_Qy));
        }
        else if (securityStrength <= 192)
        {
            _seedlen = 192;
            _outlen = 368 / 8;
            _curve = (ECCurve.Fp)NISTNamedCurves.getByName("P-384").getCurve();
            _P = new ECPoint.Fp(_curve, new ECFieldElement.Fp(_curve.getQ(), p384_Px), new ECFieldElement.Fp(_curve.getQ(), p384_Py));
            _Q = new ECPoint.Fp(_curve, new ECFieldElement.Fp(_curve.getQ(), p384_Qx), new ECFieldElement.Fp(_curve.getQ(), p384_Qy));
        }
        else if (securityStrength <= 260)
        {
            _seedlen = 256;
            _outlen = 504 / 8;
            _curve = (ECCurve.Fp)NISTNamedCurves.getByName("P-521").getCurve();
            _P = new ECPoint.Fp(_curve, new ECFieldElement.Fp(_curve.getQ(), p521_Px), new ECFieldElement.Fp(_curve.getQ(), p521_Py));
            _Q = new ECPoint.Fp(_curve, new ECFieldElement.Fp(_curve.getQ(), p521_Qx), new ECFieldElement.Fp(_curve.getQ(), p521_Qy));
        }
        else
        {
            throw new IllegalArgumentException("security strength cannot be greater than 260 bits");
        }

        _s = hash_df(seedMaterial, _seedlen);

        // System.err.println(new String(Hex.encode(_s)));
        _reseedCounter = 0;

    }

    // 1. If reseed_counter > reseed_interval, then return an indication that a
    // reseed is required.
    // 2. If (additional_input != Null), then do
    // 2.1 w = Hash (0x02 || V || additional_input).
    // 2.2 V = (V + w) mod 2^seedlen
    // .
    // 3. (returned_bits) = Hashgen (requested_number_of_bits, V).
    // 4. H = Hash (0x03 || V).
    // 5. V = (V + H + C + reseed_counter) mod 2^seedlen
    // .
    // 6. reseed_counter = reseed_counter + 1.
    // 7. Return SUCCESS, returned_bits, and the new values of V, C, and
    // reseed_counter for the new_working_state.
    public int generate(byte[] output, byte[] additionalInput, boolean predictionResistant)
    {
        int numberOfBits = output.length*8;
        
        if (predictionResistant || _reseedCounter > RESEED_MAX) 
        {   
            reseed(additionalInput);
            additionalInput = null;
        }

        if (additionalInput != null)
        {
// TODO
        }
        int m = output.length / _outlen;

        for (int i = 0; i < m; i++)
        {
            BigInteger t = new BigInteger(1, _s);

            _s = _P.multiply(t).getX().toBigInteger().toByteArray();

            //System.err.println("S: " + new String(Hex.encode(_s)));

            byte[] r = _Q.multiply(new BigInteger(1, _s)).getX().toBigInteger().toByteArray();

            System.arraycopy(r, r.length - _outlen, output, i * _outlen, _outlen);
            //System.err.println("R: " + new String(Hex.encode(r)));
        }

        if (m * _outlen < output.length)
        {
            BigInteger t = new BigInteger(1, _s);

            _s = _P.multiply(t).getX().toBigInteger().toByteArray();

            byte[] r = _Q.multiply(new BigInteger(1, _s)).getX().toBigInteger().toByteArray();

            System.arraycopy(r, 0, output, m * _outlen, output.length - (m * _outlen));
        }

        _s = _P.multiply(new BigInteger(1, _s)).getX().toBigInteger().toByteArray();

        _reseedCounter++;

        return numberOfBits;
    }

    // 1. seed_material = 0x01 || V || entropy_input || additional_input.
    //
    // 2. seed = Hash_df (seed_material, seedlen).
    //
    // 3. V = seed.
    //
    // 4. C = Hash_df ((0x00 || V), seedlen).
    //
    // 5. reseed_counter = 1.
    //
    // 6. Return V, C, and reseed_counter for the new_working_state.
    //
    // Comment: Precede with a byte of all zeros.
    public void reseed(byte[] additionalInput)
    {
        if (additionalInput == null) 
        {
            additionalInput = new byte[0];
        }

        byte[] entropy = _entropySource.getEntropy();

    }

    // 1. temp = the Null string.
    // 2. .
    // 3. counter = an 8-bit binary value representing the integer "1".
    // 4. For i = 1 to len do
    // Comment : In step 4.1, no_of_bits_to_return
    // is used as a 32-bit string.
    // 4.1 temp = temp || Hash (counter || no_of_bits_to_return ||
    // input_string).
    // 4.2 counter = counter + 1.
    // 5. requested_bits = Leftmost (no_of_bits_to_return) of temp.
    // 6. Return SUCCESS and requested_bits.
    private byte[] hash_df(byte[] seedMaterial, int seedLength)
    {
        byte[] temp = new byte[seedLength / 8];

        int len = temp.length / _digest.getDigestSize();
        int counter = 1;

        byte[] dig = new byte[_digest.getDigestSize()];

        for (int i = 0; i <= len; i++)
        {
            _digest.update((byte)counter);

            _digest.update((byte)(seedLength >> 24));
            _digest.update((byte)(seedLength >> 16));
            _digest.update((byte)(seedLength >> 8));
            _digest.update((byte)seedLength);

            _digest.update(seedMaterial, 0, seedMaterial.length);

            _digest.doFinal(dig, 0);

            int bytesToCopy = ((temp.length - i * dig.length) > dig.length)
                    ? dig.length
                    : (temp.length - i * dig.length);
            System.arraycopy(dig, 0, temp, i * dig.length, bytesToCopy);

            counter++;
        }

        return temp;
    }
}