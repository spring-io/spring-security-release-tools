/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.spring.release;

import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.util.Objects;

/**
 * A specification for a release train.
 *
 * @author Steve Riesenberg
 * @see SpringReleaseTrain
 */
public final class SpringReleaseTrainSpec {

	private final Train train;

	private final String version;

	private final WeekOfMonth weekOfMonth;

	private final DayOfWeek dayOfWeek;

	private final Year year;

	public SpringReleaseTrainSpec(Train train, String version, WeekOfMonth weekOfMonth, DayOfWeek dayOfWeek,
			Year year) {
		this.train = train;
		this.version = version;
		this.weekOfMonth = weekOfMonth;
		this.dayOfWeek = dayOfWeek;
		this.year = year;
	}

	public Train getTrain() {
		return this.train;
	}

	public String getVersion() {
		return this.version;
	}

	public WeekOfMonth getWeekOfMonth() {
		return this.weekOfMonth;
	}

	public DayOfWeek getDayOfWeek() {
		return this.dayOfWeek;
	}

	public Year getYear() {
		return this.year;
	}

	public static Builder builder() {
		return new Builder();
	}

	public enum WeekOfMonth {

		FIRST(0), SECOND(7), THIRD(14), FOURTH(21);

		private final int dayOffset;

		WeekOfMonth(int dayOffset) {
			this.dayOffset = dayOffset;
		}

		public int getDayOffset() {
			return this.dayOffset;
		}

	}

	public enum DayOfWeek {

		// @formatter:off
		MONDAY(java.time.DayOfWeek.MONDAY),
		TUESDAY(java.time.DayOfWeek.TUESDAY),
		WEDNESDAY(java.time.DayOfWeek.WEDNESDAY),
		THURSDAY(java.time.DayOfWeek.THURSDAY),
		FRIDAY(java.time.DayOfWeek.FRIDAY);
		// @formatter:on

		private final java.time.DayOfWeek dayOfWeek;

		DayOfWeek(java.time.DayOfWeek dayOfWeek) {
			this.dayOfWeek = dayOfWeek;
		}

		public java.time.DayOfWeek getDayOfWeek() {
			return this.dayOfWeek;
		}

	}

	public enum Train {

		ONE, TWO

	}

	public static final class Builder {

		private Train train;

		private String version;

		private WeekOfMonth weekOfMonth;

		private DayOfWeek dayOfWeek;

		private Year year;

		private Builder() {
		}

		public Builder train(int train) {
			switch (train) {
				case 1:
					this.train = Train.ONE;
					break;
				case 2:
					this.train = Train.TWO;
					break;
				default:
					throw new IllegalArgumentException("Invalid train: " + train);
			}
			return this;
		}

		public Builder train(Train train) {
			this.train = train;
			return this;
		}

		public Builder nextTrain() {
			// Search for next train starting with this month
			return nextTrain(LocalDate.now().withDayOfMonth(1));
		}

		public Builder nextTrain(LocalDate startDate) {
			Train nextTrain = null;

			// Search for next train from a given start date
			LocalDate currentDate = startDate;
			while (nextTrain == null) {
				if (currentDate.getMonth() == Month.JANUARY) {
					nextTrain = Train.ONE;
				}
				else if (currentDate.getMonth() == Month.JULY) {
					nextTrain = Train.TWO;
				}

				currentDate = currentDate.plusMonths(1);
			}

			return train(nextTrain).year(currentDate.getYear());
		}

		public Builder version(String version) {
			this.version = version;
			return this;
		}

		public Builder weekOfMonth(int weekOfMonth) {
			switch (weekOfMonth) {
				case 1:
					this.weekOfMonth = WeekOfMonth.FIRST;
					break;
				case 2:
					this.weekOfMonth = WeekOfMonth.SECOND;
					break;
				case 3:
					this.weekOfMonth = WeekOfMonth.THIRD;
					break;
				case 4:
					this.weekOfMonth = WeekOfMonth.FOURTH;
					break;
				default:
					throw new IllegalArgumentException("Invalid weekOfMonth: " + weekOfMonth);
			}
			return this;
		}

		public Builder weekOfMonth(WeekOfMonth weekOfMonth) {
			this.weekOfMonth = weekOfMonth;
			return this;
		}

		public Builder dayOfWeek(int dayOfWeek) {
			switch (dayOfWeek) {
				case 1:
					this.dayOfWeek = DayOfWeek.MONDAY;
					break;
				case 2:
					this.dayOfWeek = DayOfWeek.TUESDAY;
					break;
				case 3:
					this.dayOfWeek = DayOfWeek.WEDNESDAY;
					break;
				case 4:
					this.dayOfWeek = DayOfWeek.THURSDAY;
					break;
				case 5:
					this.dayOfWeek = DayOfWeek.FRIDAY;
					break;
				default:
					throw new IllegalArgumentException("Invalid dayOfWeek: " + dayOfWeek);
			}
			return this;
		}

		public Builder dayOfWeek(DayOfWeek dayOfWeek) {
			this.dayOfWeek = dayOfWeek;
			return this;
		}

		public Builder year(int year) {
			this.year = Year.of(year);
			return this;
		}

		public SpringReleaseTrainSpec build() {
			Objects.requireNonNull(this.train, "train cannot be null");
			Objects.requireNonNull(this.version, "version cannot be null");
			Objects.requireNonNull(this.weekOfMonth, "weekOfMonth cannot be null");
			Objects.requireNonNull(this.dayOfWeek, "dayOfWeek cannot be null");
			Objects.requireNonNull(this.year, "year cannot be null");
			return new SpringReleaseTrainSpec(this.train, this.version, this.weekOfMonth, this.dayOfWeek, this.year);
		}

	}

}
