/*
 * Copyright (C) 2019 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2019 University of Freiburg
 *
 * This file is part of the ULTIMATE Util Library.
 *
 * The ULTIMATE Util Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE Util Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Util Library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Util Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE Util Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.util.datastructures.relation;

import java.util.HashSet;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Implementation of an {@link AbstractRelation} that uses a {@link TreeMap}
 * for the mapping and a {@link HashSet} for the elements of the image.
 *
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 */
public class TreeRelation<D,R> extends AbstractRelation<D, R, TreeSet<R>, TreeMap<D,TreeSet<R>>> {

	@Override
	public TreeMap<D, TreeSet<R>> newMap() {
		return new TreeMap<D, TreeSet<R>>();
	}

	@Override
	public TreeSet<R> newSet() {
		return new TreeSet<R>();
	}

	public NavigableSet<D> descendingDomain() {
		return super.mMap.descendingKeySet();
	}

	@Override
	public NavigableSet<D> getDomain() {
		return super.mMap.navigableKeySet();
	}

	@Override
	public TreeSet<R> getImage(final D domainElem) {
		return mMap.get(domainElem);
	}

}
