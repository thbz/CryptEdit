package org.thbz.CryptEdit;

class WrongPasswordException extends Exception
{
    private Exception underlyingException;
    WrongPasswordException(Exception exc) {
	underlyingException = exc;
    }
}
