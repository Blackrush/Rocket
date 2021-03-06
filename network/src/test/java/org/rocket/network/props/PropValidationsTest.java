package org.rocket.network.props;

import com.google.common.collect.ImmutableList;
import org.fungsi.Either;
import org.fungsi.Unit;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.rocket.network.*;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.List;
import java.util.NoSuchElementException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

public class PropValidationsTest {

    @Retention(RetentionPolicy.RUNTIME)
    @PropPresence(String.class)
    @interface FooBar {}

    @SuppressWarnings("UnusedDeclaration")
    @FooBar
    @PropPresence(Integer.class)
    private static void annotated() {}

    @PropPresence(value = String.class, presence = false)
    private static void notPresent() {}

    private static final class BadValidator implements PropValidator {
        public BadValidator(String yo, String lo) {}
        public BadValidator(PropPresence notInStack) {}

        @Override
        public Either<Unit, Throwable> validate(NetworkClient client, PropValidated target) {
            return Unit.left();
        }
    }
    @PropValidate(BadValidator.class)
    private static void badlyValidated() {}


    private PropValidatorInstantiator instantiator;

    @Before
    public void setUp() throws Exception {
        instantiator = PropValidations::reflectiveInstantiator;
    }

    @Test
    public void testFetchValidators() throws Exception {
        // given
        AnnotatedElement target = PropValidationsTest.class.getDeclaredMethod("annotated");

        // when
        List<PropValidator> validators = PropValidations.fetchValidators(target, instantiator);

        // then
        assertThat(validators.size(), equalTo(2));
    }

    @Test
    public void testValidateSuccess() throws Exception {
        // given
        NetworkClient client = mock(NetworkClient.class);
        AnnotatedElement target = PropValidationsTest.class.getDeclaredMethod("annotated");
        List<PropValidator> validators = PropValidations.fetchValidators(target, instantiator);
        PropValidator validator = PropValidator.aggregate(ImmutableList.copyOf(validators));

        @SuppressWarnings("unchecked")
        MutProp<Object> strProp = mock(MutProp.class),
                intProp = mock(MutProp.class);
        PropId strpid = PropIds.type(String.class),
                intpid = PropIds.type(Integer.class);

        // when
        when(strProp.isDefined()).thenReturn(true);
        when(intProp.isDefined()).thenReturn(true);

        when(client.getProp(strpid)).thenReturn(strProp);
        when(client.getProp(intpid)).thenReturn(intProp);

        validator.validate(client, PropValidated.here());

        // then
        InOrder o = inOrder(client, strProp, intProp);
        o.verify(client).getProp(strpid);
        o.verify(strProp).isDefined();
        o.verify(client).getProp(intpid);
        o.verify(intProp).isDefined();
        o.verifyNoMoreInteractions();
    }

    @Test(expected = PropValidationException.class)
    public void testValidateFailure() throws Exception {
        // given
        NetworkClient client = mock(NetworkClient.class);
        AnnotatedElement target = PropValidationsTest.class.getDeclaredMethod("annotated");
        List<PropValidator> validators = PropValidations.fetchValidators(target, instantiator);
        PropValidator validator = PropValidator.aggregate(ImmutableList.copyOf(validators));

        @SuppressWarnings("unchecked")
        MutProp<Object> strProp = mock(MutProp.class),
                intProp = mock(MutProp.class);

        // when
        when(strProp.isDefined()).thenReturn(true);
        when(intProp.isDefined()).thenReturn(false);

        when(client.getProp(PropIds.type(String.class))).thenReturn(strProp);
        when(client.getProp(PropIds.type(Integer.class))).thenReturn(intProp);

        validator.hardValidate(client, PropValidated.here());

        // then
        fail();

    }

    @Test
    public void testBadValidation() throws Exception {
        try {
            PropValidations.fetchValidators(PropValidationsTest.class.getDeclaredMethod("badlyValidated"), instantiator);
            fail();
        } catch (NoSuchElementException ignored) {
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testPresenceInversion() throws Exception {
        @SuppressWarnings("unchecked")
        MutProp<Object> prop = mock(MutProp.class);
        NetworkClient client = mock(NetworkClient.class);

        Method target = PropValidationsTest.class.getDeclaredMethod("notPresent");

        when(client.getProp(PropIds.type(String.class))).thenReturn(prop);
        when(prop.isDefined()).thenReturn(false);

        List<PropValidator> validators = PropValidations.fetchValidators(target, instantiator);
        PropValidator first = validators.get(0);
        Either<Unit, Throwable> result = first.validate(client, PropValidated.here());

        assertEquals("returned validators number", 1, validators.size());
        assertTrue("result is a success", result.isLeft());
    }
}